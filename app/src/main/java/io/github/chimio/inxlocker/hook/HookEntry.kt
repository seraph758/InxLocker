package io.github.chimio.inxlocker.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.core.annotation.LegacyHookApi
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import io.github.chimio.inxlocker.util.Broadcasts
import io.github.chimio.inxlocker.util.IntentAnalyzer
import io.github.chimio.inxlocker.util.IntentRedirector
import io.github.chimio.inxlocker.util.PrefsProvider
import io.github.chimio.inxlocker.util.e
import io.github.chimio.inxlocker.util.i

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    companion object {

        private const val TAG = "InstallerRedirect"
    }

    private fun registerPrefsUpdateReceiver(context: Context?) {
        if (context == null) return
        try {
            val filter = IntentFilter(Broadcasts.ACTION_PREFS_UPDATED)
            ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    IntentRedirector.reloadPrefs()
                }
            }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Throwable) {
        }
    }

    override fun onInit() = configs {
        debugLog {
            tag = "InxLocker"
            PrefsProvider.reload()
            isEnable = PrefsProvider.getBoolean("enable_debug_log", true)
        }
    }

    override fun onHook() = encase {
        loadApp {
            if (packageName == "android") return@loadApp
            hookContextStartActivity()
            registerPrefsUpdateReceiver(appContext)
        }
        loadSystem {
            hookActivityStarterExecute()
        }
    }

    //Hook Context.startActivity
    @OptIn(LegacyHookApi::class)
    private fun PackageParam.hookContextStartActivity() {

        // Hook ContextWrapper.startActivity
        "android.content.ContextWrapper".toClassOrNull()?.apply {
            resolve().firstMethod {
                name = "startActivity"
                parameters(Intent::class.java)
            }.hook {
                before {
                    try {
                        PrefsProvider.reload()
                        val intent = args(0).cast<Intent>()
                        handleIntentIfNeeded(intent, "ContextWrapper.startActivity") {
                        }
                    } catch (e: Exception) {
                        YLog.e(TAG, "Hook ContextWrapper.startActivity 错误: ${e.message}", e)
                    }
                }
            }
        }

        // Hook Activity.startActivity
        "android.app.Activity".toClassOrNull()?.apply {
            resolve().firstMethod {
                name = "startActivity"
                parameters(Intent::class.java)
            }.hook {
                before {
                    try {
                        PrefsProvider.reload()
                        val intent = args(0).cast<Intent>()
                        handleIntentIfNeeded(intent, "Activity.startActivity")
                    } catch (e: Exception) {
                        YLog.e(TAG, "Hook Activity.startActivity 错误: ${e.message}", e)
                    }
                }
            }
        }

        // Hook Activity.startActivityForResult
        "android.app.Activity".toClassOrNull()?.apply {
            resolve().firstMethod {
                name = "startActivityForResult"
                parameters(Intent::class.java, Int::class.javaPrimitiveType!!)
            }.hook {
                before {
                    try {
                        PrefsProvider.reload()
                        val intent = args(0).cast<Intent>()
                        handleIntentIfNeeded(intent, "Activity.startActivityForResult")
                    } catch (e: Exception) {
                        YLog.e(TAG, "Hook startActivityForResult 错误: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun handleIntentIfNeeded(
        intent: Intent?,
        source: String,
        onRedirect: (() -> Unit)? = null
    ) {
        YLog.i(TAG, "==> $source: 开始处理Intent$intent")

        intent?.let {
            // 白名单：系统关键组件直接放行，永不拦截
            val component = it.component?.className ?: ""
            if (component.contains("com.android.systemui") ||
                component.contains("com.android.settings") ||
                component.contains("com.android.packageinstaller") ||
                component.contains("org.lsposed.manager") ||
                component.contains("me.weishu.exp") ||
                it.action == "android.intent.action.BOOT_COMPLETED") {
                return@let
            }

            // 强制判断：只要是安装相关就干掉（包含应用宝写死 cmp 的）
            val isInstallIntent = 
                it.action == Intent.ACTION_VIEW && it.data?.scheme == "content" ||
                it.action == Intent.ACTION_VIEW && it.data?.scheme == "file" && it.dataString?.endsWith(".apk") == true ||
                it.action == Intent.ACTION_INSTALL_PACKAGE ||
                it.type == "application/vnd.android.package-archive" ||
                component.contains("Installer") || component.contains("Install") ||
                it.`package` == "com.tencent.android.qqdownloader"  // 专杀应用宝

            if (isInstallIntent) {
                YLog.i(TAG, "$source: 【安全强制拦截】检测到安装行为，直接重定向！")
                IntentRedirector.redirect(it, TAG)
                onRedirect?.invoke()
            } else {
                YLog.i(TAG, "$source: 非安装类Intent，放行")
            }
        }
    }
    fun PackageParam.hookActivityStarterExecute() {
        "com.android.server.wm.ActivityStarter".toClassOrNull()?.apply {
            resolve().firstMethod {
                name = "execute"
            }.hook {
                before {
                    try {
                        PrefsProvider.reload()
                        val mRequestField = instanceClass?.getDeclaredField("mRequest")
                            ?.apply { isAccessible = true }
                            ?: throw NoSuchFieldException("mRequest field not found")

                        val requestObject = mRequestField.get(instance)
                            ?: throw NullPointerException("Request object is null")

                        val requestClass =
                            "com.android.server.wm.ActivityStarter\$Request".toClassOrNull()
                                ?: throw NullPointerException("ActivityStarter\$Request class not found")

                        val intentField = requestClass.getDeclaredField("intent")
                            .apply { isAccessible = true }

                        val intent = intentField.get(requestObject) as? Intent

                        handleIntentIfNeeded(intent, "ActivityStarter.execute") {
                            intent?.let { modifiedIntent ->
                                intentField.set(requestObject, modifiedIntent)
                            }
                        }
                    } catch (e: Exception) {
                        YLog.e(TAG, "ActivityStarter.execute Hook 错误: ${e.message}", e)
                    }
                }
            }
        }
    }

}

