package com.omarea.shell

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.support.v4.content.PermissionChecker
import android.util.Log
import com.omarea.common.shell.KeepShellPublic
import com.omarea.shared.CommonCmds
import com.omarea.vtools.R

/**
 * 检查获取root权限
 * Created by helloklf on 2017/6/3.
 */

class CheckRootStatus(var context: Context, private var next: Runnable? = null, private var disableSeLinux: Boolean = false) {
    var myHandler: Handler = Handler(Looper.getMainLooper())

    var therad: Thread? = null
    fun forceGetRoot() {
        if(lastCheckResult) {
            myHandler.post {
                if (next != null)
                    next!!.run()
            }
            return
        }

        var completed = false
        therad = Thread {
            rootStatus = KeepShellPublic.checkRoot()
            if (rootStatus && disableSeLinux) {
                KeepShellPublic.doCmdSync(CommonCmds.DisableSELinux)
            }
            if (!rootStatus) {
                completed = true
                myHandler.post {
                    KeepShellPublic.tryExit()
                    val alert = AlertDialog.Builder(context)
                    alert.setCancelable(false)
                    alert.setTitle(R.string.error_root)
                    alert.setNegativeButton(R.string.btn_retry, { _, _ ->
                        if (therad != null && therad!!.isAlive && !therad!!.isInterrupted) {
                            therad!!.interrupt()
                            therad = null
                        }
                        forceGetRoot()
                    })
                    alert.setNeutralButton(R.string.btn_exit, { _, _ ->
                        System.exit(0)
                        //android.os.Process.killProcess(android.os.Process.myPid())
                    })
                    val dialog = alert.create()
                    dialog.window!!.setWindowAnimations(R.style.windowAnim)
                    dialog.show()
                }
            } else {
                completed = true
                myHandler.post {
                    if (next != null)
                        next!!.run()
                }
            }
        };
        therad!!.start()
        myHandler.postDelayed({
            if (!completed) {
                val alert = AlertDialog.Builder(context)
                alert.setCancelable(false)
                alert.setTitle(R.string.error_root)
                alert.setMessage(R.string.error_su_timeout)
                alert.setNegativeButton(R.string.btn_retry, { _, _ ->
                    if (therad != null && therad!!.isAlive && !therad!!.isInterrupted) {
                        therad!!.interrupt()
                        therad = null
                    }
                    forceGetRoot()
                })
                alert.setNeutralButton(R.string.btn_exit, { _, _ ->
                    System.exit(0)
                    //android.os.Process.killProcess(android.os.Process.myPid())
                })
                val dialog = alert.create()
                dialog.window!!.setWindowAnimations(R.style.windowAnim)
                dialog.show()
            }
        }, 15000)
    }

    companion object {
        private var rootStatus = false
        private fun checkPermission(context: Context, permission: String): Boolean = PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED
        fun grantPermission(context: Context) {
            val cmds = StringBuilder()
            // 必需的权限
            val requiredPermission = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CHANGE_CONFIGURATION,
                    Manifest.permission.WRITE_SECURE_SETTINGS,
                    Manifest.permission.SYSTEM_ALERT_WINDOW
                    // Manifest.permission.UNINSTALL_SHORTCUT,
                    // Manifest.permission.INSTALL_SHORTCUT
            )
            requiredPermission.forEach {
                if (it == Manifest.permission.SYSTEM_ALERT_WINDOW) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (!Settings.canDrawOverlays(context)) {
                            // 未允许悬浮窗
                            try {
                                //启动Activity让用户授权
                                // Toast.makeText(context, "Scene未获得显示悬浮窗权限", Toast.LENGTH_SHORT).show()
                                // val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.getPackageName()));
                                // context.startActivity(intent);
                            } catch (ex: Exception) {

                            }
                        }
                    } else {
                        if (!checkPermission(context, it)) {
                            cmds.append("pm grant ${context.packageName} $it\n")
                        }
                    }
                } else {
                    if (!checkPermission(context, it)) {
                        cmds.append("pm grant ${context.packageName} $it\n")
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!checkPermission(context, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) {
                    cmds.append("dumpsys deviceidle whitelist +${context.packageName};\n")
                }
            }

            /*
            // 不支持使用ROOT权限进行设置
            if (!checkPermission(context, Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)) {
                cmds.append("pm grant ${context.packageName} android.permission.BIND_NOTIFICATION_LISTENER_SERVICE;\n")
            }
            if (!checkPermission(context, Manifest.permission.WRITE_SETTINGS)) {
                cmds.append("pm grant ${context.packageName} android.permission.WRITE_SETTINGS;\n")
            }
            */
            KeepShellPublic.doCmdSync(cmds.toString())
        }

        // 最后的ROOT检测结果
        val lastCheckResult: Boolean
            get (){
                return this.rootStatus
            }

        public fun isMagisk(): Boolean {
            return SysUtils.executeCommandWithOutput(false, "su -v").contains("MAGISKSU")
        }

        public fun isTmpfs(dir: String): Boolean {
            return SysUtils.executeCommandWithOutput(false, " df | grep tmpfs | grep \"$dir\"").trim().isNotEmpty()
        }
    }
}
