package com.citynettv

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.*

/**
 * CloudStream plugini üçün giriş dialoqu.
 * Plugin.openSettings lambda-sından çağırılır.
 */
object CityNetTVSettingsDialog {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(context: Context) {
        val prefs = context.getSharedPreferences("citynettv_prefs", Context.MODE_PRIVATE)

        // ── Root layout ───────────────────────────────
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        // ── Status ────────────────────────────────────
        val savedUser = prefs.getString("citynettv_username", "") ?: ""
        val isLoggedIn = !prefs.getString("citynettv_access_token", null).isNullOrEmpty()

        val statusView = TextView(context).apply {
            text = if (isLoggedIn) "✅ Giriş edilib: $savedUser"
                   else "❌ Giriş edilməyib"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        root.addView(statusView)

        // ── Username ──────────────────────────────────
        root.addView(TextView(context).apply {
            text = "İstifadəçi adı (telefon / email)"
            textSize = 13f
            setPadding(0, 0, 0, 8)
        })

        val usernameField = EditText(context).apply {
            hint = "örn: +994501234567"
            setText(savedUser)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(24, 24, 24, 24)
        }
        root.addView(usernameField)

        // ── Password ──────────────────────────────────
        root.addView(TextView(context).apply {
            text = "Şifrə"
            textSize = 13f
            setPadding(0, 16, 0, 8)
        })

        val passwordField = EditText(context).apply {
            hint = "Şifrənizi daxil edin"
            setText(prefs.getString("citynettv_password", "") ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(24, 24, 24, 24)
        }
        root.addView(passwordField)

        // ── Message ───────────────────────────────────
        val msgView = TextView(context).apply {
            text = ""
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        root.addView(msgView)

        // ── Scrollable wrapper ────────────────────────
        val scroll = ScrollView(context).apply { addView(root) }

        // ── Dialog ────────────────────────────────────
        val dialog = AlertDialog.Builder(context)
            .setTitle("🔐 CityNetTV Giriş")
            .setView(scroll)
            .setPositiveButton("Giriş Et", null)   // handler set later to prevent auto-dismiss
            .setNeutralButton("Çıxış") { _, _ ->
                prefs.edit()
                    .remove("citynettv_access_token")
                    .remove("citynettv_refresh_token")
                    .remove("citynettv_user_uid")
                    .remove("citynettv_profile_id")
                    .apply()
                statusView.text = "❌ Giriş edilməyib"
                msgView.text = "Çıxış edildi."
            }
            .setNegativeButton("Bağla", null)
            .create()

        dialog.show()

        // Override positive-button click so dialog stays open during login
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val user = usernameField.text.toString().trim()
            val pass = passwordField.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                msgView.text = "⚠️ İstifadəçi adı və şifrəni daxil edin"
                return@setOnClickListener
            }

            // Save credentials
            prefs.edit()
                .putString("citynettv_username", user)
                .putString("citynettv_password", pass)
                .remove("citynettv_access_token")
                .remove("citynettv_refresh_token")
                .apply()

            msgView.text = "🔄 Giriş edilir..."

            // Run login in background thread (avoids coroutine dependency issues)
            Thread {
                try {
                    val api = CityNetTVApi(prefs)
                    // Call the blocking login helper
                    val ok = loginBlocking(api, user, pass)

                    mainHandler.post {
                        if (ok) {
                            statusView.text = "✅ Giriş edildi: $user"
                            msgView.text = "✅ Uğurla giriş edildi! Ana səhifəni yeniləyin."
                        } else {
                            msgView.text = "❌ Giriş alınmadı. İstifadəçi adı / şifrəni yoxlayın."
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    mainHandler.post {
                        msgView.text = "❌ Xəta baş verdi: ${e.message}"
                    }
                }
            }.start()
        }
    }

    /**
     * Runs the suspend login function in a blocking coroutine context.
     * Uses kotlinx.coroutines.runBlocking which is bundled with CloudStream.
     */
    private fun loginBlocking(api: CityNetTVApi, user: String, pass: String): Boolean {
        return try {
            kotlinx.coroutines.runBlocking {
                api.login(user, pass)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
