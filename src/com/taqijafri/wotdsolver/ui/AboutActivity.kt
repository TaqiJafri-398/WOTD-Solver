package com.taqijafri.wotdsolver.ui

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import com.taqijafri.wotdsolver.R

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val email = findViewById<TextView>(R.id.tvEmail)
        email.paintFlags = email.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        email.setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_SENDTO,
                        Uri.parse("mailto:taqijafri398@gmail.com")
                    )
                )
            } catch (e: Exception) {
                // no email app installed; nothing to do
            }
        }

        val phone = findViewById<TextView>(R.id.tvPhone)
        phone.paintFlags = phone.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        phone.setOnClickListener {
            // Open WhatsApp chat with the developer, pre-filled greeting.
            try {
                val text = java.net.URLEncoder.encode("hi developer", "UTF-8").replace("+", "%20")
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/923077982214?text=$text")
                    )
                )
            } catch (e: Exception) {
                // WhatsApp / browser unavailable: fall back to the dialer.
                try {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:03077982214")))
                } catch (_: Exception) {
                }
            }
        }
    }
}
