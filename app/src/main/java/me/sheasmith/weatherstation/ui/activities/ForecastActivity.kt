package me.sheasmith.weatherstation.ui.activities

import android.animation.LayoutTransition
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import kotlinx.android.synthetic.main.activity_forecast.*
import me.sheasmith.weatherstation.ApiManager
import me.sheasmith.weatherstation.R
import me.sheasmith.weatherstation.helpers.ForecastHelper
import me.sheasmith.weatherstation.models.ApiResponse
import me.sheasmith.weatherstation.models.Forecast
import me.sheasmith.weatherstation.models.UnauthorisedException
import me.sheasmith.weatherstation.ui.activities.settings.ApiKeyActivity
import me.sheasmith.weatherstation.ui.adapters.ViewPagerFragmentAdapter
import me.sheasmith.weatherstation.ui.fragments.ForecastFragment
import java.util.*

class ForecastActivity : AppCompatActivity() {
    var lastUpdatedDate = Date()
    var forecasts: List<Forecast>? = null
    var selectedForecast = 0
    private var paddingTop = 0
    private var paddingBottom = 0
    val adapter = ViewPagerFragmentAdapter(supportFragmentManager, lifecycle)
    var flags = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forecast)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = 0x00000000 // transparent
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val flags = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            window.addFlags(flags)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }

        flags = window.decorView.systemUiVisibility

        doRequest()

        val layoutTransition: LayoutTransition = swipeRefresh.layoutTransition
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        }

        swipeRefresh.setOnRefreshListener {
            doRequest()
        }

        viewPager.adapter = adapter

        forecastBar.setOnNavigationItemSelectedListener {
            selectedForecast = it.itemId
            viewPager.currentItem = selectedForecast
            val isLight = adapter.getFragment(it.itemId).isLight()

            if (isLight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.decorView.systemUiVisibility = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            else {
                window.decorView.systemUiVisibility = flags
            }

            true
        }

        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                selectedForecast = position
                forecastBar.selectedItemId = selectedForecast

                val isLight = adapter.getFragment(selectedForecast).isLight()

                if (isLight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    window.decorView.systemUiVisibility = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
                else {
                    window.decorView.systemUiVisibility = flags
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                swipeRefresh.isEnabled = state == ViewPager2.SCROLL_STATE_IDLE
                super.onPageScrollStateChanged(state)
            }
        })
    }

    private fun doRequest() {
        ApiManager.getForecast(object : ApiResponse<List<Forecast>> {
            override fun success(value: List<Forecast>) {
                lastUpdatedDate = Date()
                forecasts = value

                val menu = forecastBar.menu

                var index = 0
                runOnUiThread {
                    adapter.clearFragments()

                    menu.clear()
                    value.forEach {
                        val iconCode = if (it.iconCodeDay != -1) it.iconCodeDay else it.iconCodeNight

                        if (index != 5)
                            menu.add(Menu.NONE, index, Menu.NONE, it.dayOfWeek.substring(0, 3)).setIcon(ForecastHelper.getIcon(iconCode))
                        index++

                        adapter.addFragment(ForecastFragment(it, lastUpdatedDate))
                    }

                    viewPager.currentItem = selectedForecast
                    adapter.notifyDataSetChanged()

                    forecastBar.selectedItemId = selectedForecast
                    viewPager.visibility = View.VISIBLE
                    forecastBar.visibility = View.VISIBLE
                    loader.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }
            }

            override fun error(e: Exception?) {
                runOnUiThread {
                    if (e is UnauthorisedException) {
                        AlertDialog.Builder(this@ForecastActivity)
                                .setTitle("Invalid API Key")
                                .setMessage("It appears as if your API Key is invalid")
                                .setPositiveButton("Fix") { _, _ ->
                                    startActivityForResult(Intent(this@ForecastActivity, ApiKeyActivity::class.java), 2)
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .create()
                                .show()
                    } else {
                        AlertDialog.Builder(this@ForecastActivity)
                                .setTitle("No Internet")
                                .setMessage("You do not appear to be connected to the internet. Please check your connection and try again.")
                                .setPositiveButton("Retry") { _, _ ->
                                    doRequest()
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .create()
                                .show()
                    }
                }
            }

        }, this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            this.paddingTop = insets.systemWindowInsetTop
            this.paddingBottom = insets.systemWindowInsetBottom

            if (paddingBottom != 0 && paddingTop != 0) {
                adapter.setPadding(paddingTop, paddingBottom)
            }

            insets.consumeSystemWindowInsets()
            insets
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 2) {
            swipeRefresh.isRefreshing = true
            doRequest()
        }
    }
}