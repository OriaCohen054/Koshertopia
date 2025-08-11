package com.example.koshertopia
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

open class BaseActivity : androidx.appcompat.app.AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge מודרני
        enableEdgeToEdge()
    }

    override fun onResume() {
        super.onResume()
        // שלא ייכבה המסך
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * לקרוא אחרי setContentView(...)
     * rootId = ה-ConstraintLayout הראשי
     * scrollId = ה-NestedScrollView (אם יש)
     * bottomBarId = הסרגל התחתון (אם יש)
     */
    protected fun applyEdgeToEdge(rootId: Int, scrollId: Int?, bottomBarId: Int?) {
        val root: View = findViewById(rootId)
        val barHeightPx = resources.getDimensionPixelSize(
            resources.getIdentifier("bottom_bar_height", "dimen", packageName)
        ).let { if (it == 0) dp(56) else it }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 1) מרים את הסרגל בעזרת MARGIN כך שלא ייחתך
            bottomBarId?.let { id ->
                val bar: View = findViewById(id)
                bar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = sys.bottom
                }
            }

            // 2) מוסיף ריווח תוכן בהתאם לגובה הסרגל + ה-inset
            scrollId?.let { id ->
                val scroll: View = findViewById(id)
                scroll.updatePadding(bottom = barHeightPx + sys.bottom)
            }

            insets
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()
}
