package com.citynettv

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class CityNetTVSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("citynettv_prefs", Context.MODE_PRIVATE)

        // ── Root layout ──────────────────────────────────────────────────────
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // ── Title ────────────────────────────────────────────────────────────
        root.addView(TextView(ctx).apply {
            text = "🔐 CityNetTV Giriş"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        // ── Status ───────────────────────────────────────────────────────────
        val savedUser = prefs.getString("citynettv_username", "") ?: ""
        val isLoggedIn = prefs.getString("citynettv_access_token", null) != null

        val statusView = TextView(ctx).apply {
            text = if (isLoggedIn) "✅ Giriş edilib: $savedUser"
                   else "❌ Giriş edilməyib"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        root.addView(statusView)

        // ── Username ─────────────────────────────────────────────────────────
        root.addView(TextView(ctx).apply {
            text = "İstifadəçi adı (telefon/email)"
            textSize = 13f
            setPadding(0, 0, 0, 8)
        })

        val usernameField = EditText(ctx).apply {
            hint = "örn: +994501234567"
            setText(savedUser)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(16, 16, 16, 16)
        }
        root.addView(usernameField)

        // ── Password ─────────────────────────────────────────────────────────
        root.addView(TextView(ctx).apply {
            text = "Şifrə"
            textSize = 13f
            setPadding(0, 16, 0, 8)
        })

        val passwordField = EditText(ctx).apply {
            hint = "Şifrənizi daxil edin"
            setText(prefs.getString("citynettv_password", "") ?: "")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(16, 16, 16, 16)
        }
        root.addView(passwordField)

        // ── Buttons ──────────────────────────────────────────────────────────
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 0)
        }

        val loginBtn = Button(ctx).apply {
            text = "Giriş Et"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginEnd = 8 }
        }

        val logoutBtn = Button(ctx).apply {
            text = "Çıxış"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginStart = 8 }
        }

        btnRow.addView(loginBtn)
        btnRow.addView(logoutBtn)
        root.addView(btnRow)

        // ── Message ──────────────────────────────────────────────────────────
        val msgView = TextView(ctx).apply {
            text = ""
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        root.addView(msgView)

        // ── Login action ─────────────────────────────────────────────────────
        loginBtn.setOnClickListener {
            val user = usernameField.text.toString().trim()
            val pass = passwordField.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                msgView.text = "⚠️ İstifadəçi adı və şifrəni daxil edin"
                return@setOnClickListener
            }

            // Save credentials first
            prefs.edit()
                .putString("citynettv_username", user)
                .putString("citynettv_password", pass)
                // Clear old token so next API call forces re-login
                .remove("citynettv_access_token")
                .remove("citynettv_refresh_token")
                .apply()

            msgView.text = "🔄 Giriş edilir..."

            // Launch coroutine to actually log in
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val api = CityNetTVApi(prefs)
                val ok = api.login(user, pass)
                activity?.runOnUiThread {
                    if (ok) {
                        statusView.text = "✅ Giriş edildi: $user"
                        msgView.text = "✅ Uğurla giriş edildi! Ana səhifəni yeniləyin."
                    } else {
                        msgView.text = "❌ Giriş alınmadı. İstifadəçi adı/şifrəni yoxlayın."
                    }
                }
            }
        }

        // ── Logout action ────────────────────────────────────────────────────
        logoutBtn.setOnClickListener {
            prefs.edit()
                .remove("citynettv_access_token")
                .remove("citynettv_refresh_token")
                .remove("citynettv_user_uid")
                .remove("citynettv_profile_id")
                .apply()
            statusView.text = "❌ Giriş edilməyib"
            msgView.text = "Çıxış edildi."
        }

        return ScrollView(ctx).apply { addView(root) }
    }
}
