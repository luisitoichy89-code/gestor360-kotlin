package com.gestor360.app
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
class VentasActivity : android.app.Activity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var etBuscar: EditText
    private lateinit var panelSugerencias: LinearLayout
    private lateinit var panelCarrito: LinearLayout
    private lateinit var panelTotal: LinearLayout
    private lateinit var tvTotal: TextView
    private var carrito = mutableListOf<Triple<Int, String, Double>>()
    private var almacenId = "1"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ventas)
        dbHelper = DatabaseHelper(this)
        almacenId = intent.getStringExtra("almacen_id") ?: "1"
        etBuscar = findViewById(R.id.etBuscar)
        panelSugerencias = findViewById(R.id.panelSugerencias)
        panelCarrito = findViewById(R.id.panelCarrito)
        panelTotal = findViewById(R.id.panelTotal)
        tvTotal = findViewById(R.id.tvTotal)
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { buscar(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        findViewById<Button>(R.id.btnEfectivo).setOnClickListener { vender("cash") }
        findViewById<Button>(R.id.btnTransferencia).setOnClickListener { vender("transfer") }
    }
    private fun buscar(q: String) {
        panelSugerencias.removeAllViews()
        if (q.length < 2) { panelSugerencias.visibility = View.GONE; return }
        val c = dbHelper.readableDatabase.rawQuery("SELECT * FROM productos WHERE nombre LIKE ? AND almacen_id=? LIMIT 5", arrayOf("%${q.uppercase()}%", almacenId))
        if (c.count == 0) { panelSugerencias.visibility = View.GONE; c.close(); return }
        panelSugerencias.visibility = View.VISIBLE
        while (c.moveToNext()) {
            val tv = TextView(this).apply {
                text = "${c.getString(c.getColumnIndexOrThrow("nombre"))} — ${c.getDouble(c.getColumnIndexOrThrow("precio"))} CUP"
                setTextColor(0xFFe8eaf0.toInt()); setBackgroundColor(0xFF2d3444.toInt()); setPadding(14,12,14,12); textSize = 14f
                setOnClickListener {
                    carrito.add(Triple(c.getInt(0), c.getString(1), c.getDouble(2)))
                    refrescar()
                    etBuscar.text.clear(); panelSugerencias.visibility = View.GONE
                }
            }
            panelSugerencias.addView(tv)
        }
        c.close()
    }
    private fun refrescar() {
        panelCarrito.removeAllViews()
        var t = 0.0
        carrito.forEach { item ->
            t += item.third
            panelCarrito.addView(TextView(this).apply {
                text = "${item.second} — ${item.third} CUP"; setTextColor(0xFFe8eaf0.toInt()); setPadding(14,10,14,10)
            })
        }
        if (carrito.isNotEmpty()) { panelTotal.visibility = View.VISIBLE; tvTotal.text = "%.2f CUP".format(t) }
        else panelTotal.visibility = View.GONE
    }
    private fun vender(metodo: String) {
        if (carrito.isEmpty()) { Toast.makeText(this@VentasActivity, "Carrito vacío", Toast.LENGTH_SHORT).show(); return }
        val db = dbHelper.writableDatabase
        for (item in carrito) {
            db.execSQL("INSERT INTO ventas (producto_id, producto_nombre, cantidad, precio_unit, total, metodo, efectivo, transferencia, usuario_id, almacen_id, created_at) VALUES (?,?,1,?,?,?,?,?,1,?,datetime('now'))",
                arrayOf<Any>(item.first, item.second, item.third, item.third, metodo, if(metodo=="cash")item.third else 0, if(metodo=="transfer")item.third else 0, almacenId))
            db.execSQL("UPDATE productos SET stock=MAX(0,stock-1) WHERE id=?", arrayOf(item.first))
        }
        Toast.makeText(this@VentasActivity, String.format("Venta: %.2f CUP", carrito.sumOf { it.third }), Toast.LENGTH_LONG).show()
        carrito.clear(); refrescar()
    }
}
