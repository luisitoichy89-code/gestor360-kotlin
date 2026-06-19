package com.gestor360.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : android.app.Activity() {

    private lateinit var sidebar: LinearLayout
    private lateinit var tvTitulo: TextView
    private lateinit var tvBadge: TextView
    private lateinit var tvUsuarioSidebar: TextView

    private lateinit var dashboard: LinearLayout
    private lateinit var ventas: LinearLayout
    private lateinit var productos: LinearLayout
    private lateinit var inventario: LinearLayout
    private lateinit var contador: LinearLayout
    private lateinit var stockReal: LinearLayout
    private lateinit var chat: LinearLayout

    private var usuario = ""
    private var rol = ""
    private var almacenId = "1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usuario = intent.getStringExtra("usuario") ?: "Admin"
        rol = intent.getStringExtra("rol") ?: "superadmin"
        almacenId = intent.getStringExtra("almacen_id") ?: "1"

        sidebar = findViewById(R.id.sidebar)
        tvTitulo = findViewById(R.id.tvTitulo)
        tvBadge = findViewById(R.id.tvBadge)
        tvUsuarioSidebar = findViewById(R.id.tvUsuario)

        tvBadge.text = "Local $almacenId"
        tvUsuarioSidebar.text = "$usuario ($rol)"

        dashboard = findViewById(R.id.panelDashboard)
        ventas = findViewById(R.id.panelVentas)
        productos = findViewById(R.id.panelProductos)
        inventario = findViewById(R.id.panelInventario)
        contador = findViewById(R.id.panelContador)
        stockReal = findViewById(R.id.panelStockReal)
        chat = findViewById(R.id.panelChat)

        findViewById<Button>(R.id.btnMenu).setOnClickListener { sidebar.visibility = android.view.View.VISIBLE }
        findViewById<Button>(R.id.btnBack).setOnClickListener { showPanel("Dashboard") }

        findViewById<View>(R.id.cardVentas).setOnClickListener {
            startActivity(Intent(this, VentasActivity::class.java).putExtra("almacen_id", almacenId))
        }
        findViewById<View>(R.id.cardInventario).setOnClickListener { showPanel("Inventario") }
        findViewById<View>(R.id.cardProductos).setOnClickListener { showPanel("Productos") }
        findViewById<View>(R.id.cardContador).setOnClickListener { showPanel("Contador") }

        findViewById<Button>(R.id.menuDashboard).setOnClickListener { showPanel("Dashboard") }
        findViewById<Button>(R.id.menuVentas).setOnClickListener {
            startActivity(Intent(this, VentasActivity::class.java).putExtra("almacen_id", almacenId))
            // drawer no disponible sin AppCompat
        }
        findViewById<Button>(R.id.menuProductos).setOnClickListener { showPanel("Productos") }
        findViewById<Button>(R.id.menuInventario).setOnClickListener { showPanel("Inventario") }
        findViewById<Button>(R.id.menuContador).setOnClickListener { showPanel("Contador") }
        findViewById<Button>(R.id.menuStockReal).setOnClickListener { showPanel("StockReal") }
        findViewById<Button>(R.id.menuChat).setOnClickListener { showPanel("Chat") }

        findViewById<Button>(R.id.menuLogout).setOnClickListener {
            Toast.makeText(this@MainActivity, "Cerrando sesión...", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showPanel(name: String) {
        dashboard.visibility = View.GONE
        ventas.visibility = View.GONE
        productos.visibility = View.GONE
        inventario.visibility = View.GONE
        contador.visibility = View.GONE
        stockReal.visibility = View.GONE
        chat.visibility = View.GONE
        when (name) {
            "Dashboard" -> dashboard.visibility = View.VISIBLE
            "Ventas" -> ventas.visibility = View.VISIBLE
            "Productos" -> productos.visibility = View.VISIBLE
            "Inventario" -> inventario.visibility = View.VISIBLE
            "Contador" -> contador.visibility = View.VISIBLE
            "StockReal" -> stockReal.visibility = View.VISIBLE
            "Chat" -> chat.visibility = View.VISIBLE
        }
        tvTitulo.text = if (name == "StockReal") "Stock Real" else name
        // drawer no disponible sin AppCompat
    }
}
