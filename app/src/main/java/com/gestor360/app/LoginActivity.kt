package com.gestor360.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class LoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etUser = findViewById<EditText>(R.id.etUsuario)
        val etPass = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            if (etUser.text.toString() == "admin" && etPass.text.toString() == "1234") {
                Toast.makeText(this@LoginActivity, "Bienvenido", Toast.LENGTH_SHORT).show()
            startActivity(android.content.Intent(this@LoginActivity, MainActivity::class.java))
            finish()
            } else {
                Toast.makeText(this@LoginActivity, "Credenciales inválidas", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
