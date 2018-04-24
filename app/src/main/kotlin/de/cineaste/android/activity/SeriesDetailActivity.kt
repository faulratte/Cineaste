package de.cineaste.android.activity

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CollapsingToolbarLayout
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Toast
import com.squareup.picasso.Picasso
import de.cineaste.android.R
import de.cineaste.android.adapter.series.SeriesDetailAdapter
import de.cineaste.android.database.dao.BaseDao
import de.cineaste.android.database.dbHelper.SeriesDbHelper
import de.cineaste.android.entity.series.Series
import de.cineaste.android.listener.ItemClickListener
import de.cineaste.android.network.SeriesCallback
import de.cineaste.android.network.SeriesLoader
import de.cineaste.android.util.Constants
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch

class SeriesDetailActivity : AppCompatActivity(), ItemClickListener, SeriesDetailAdapter.SeriesStateManipulationClickListener, View.OnClickListener {

    private var state: Int = 0
    private var poster: ImageView? = null

    private var seriesId: Long = 0
    private lateinit var seriesDbHelper: SeriesDbHelper
    private lateinit var seriesLoader: SeriesLoader
    private var currentSeries: Series? = null
    private lateinit var progressBar: View
    private lateinit var fab: FloatingActionButton
    private lateinit var layout: RecyclerView
    private lateinit var updateCallBack: Runnable
    private lateinit var adapter: SeriesDetailAdapter

    override fun onClick(v: View) {
        if (v.id == R.id.poster) {
            val intent = Intent(this@SeriesDetailActivity, PosterActivity::class.java)
            intent.putExtra(PosterActivity.POSTER_PATH, currentSeries!!.posterPath!!)
            startActivity(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.detail_menu, menu)
        val toWatchList = menu.findItem(R.id.action_to_watchlist)
        val toHistory = menu.findItem(R.id.action_to_history)
        val delete = menu.findItem(R.id.action_delete)

        for (i in 0 until menu.size()) {
            val drawable = menu.getItem(i).icon
            if (drawable != null) {
                drawable.mutate()
                drawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
            }
        }

        when (state) {
            R.string.searchState -> {
                delete.isVisible = false
                toHistory.isVisible = true
                toWatchList.isVisible = true
            }
            R.string.historyState -> {
                delete.isVisible = true
                toHistory.isVisible = false
                toWatchList.isVisible = true
            }
            R.string.watchlistState -> {
                delete.isVisible = true
                toHistory.isVisible = true
                toWatchList.isVisible = false
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }

            R.id.action_delete -> {
                onDeleteClicked()
                return true
            }
            R.id.action_to_history -> {
                onAddToHistoryClicked()
                return true
            }
            R.id.action_to_watchlist -> {
                onAddToWatchClicked()
                return true
            }
            R.id.share -> {
                val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
                sharingIntent.type = "text/plain"
                val shareBodyText = "${currentSeries!!.name} ${Constants.THE_MOVIE_DB_SERIES_URI.replace("<SERIES_ID>", currentSeries!!.id.toString())}"
                sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.share_series))
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBodyText)
                startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_series)))
                return true
            }
        }
        return true
    }

    override fun onDeleteClicked() {
        layout.removeCallbacks(updateCallBack)
        seriesDbHelper.delete(seriesId)
        currentSeries = null
        onBackPressed()
    }

    override fun onAddToHistoryClicked() {
        var seriesCallback: SeriesCallback? = null

        when (state) {
            R.string.searchState ->

                seriesCallback = object : SeriesCallback {
                    override fun onFailure() {

                    }

                    override fun onSuccess(series: Series) {
                        showDialogIfNeeded(series)
                    }
                }
            R.string.watchlistState -> showDialogIfNeeded(currentSeries!!)
        }

        if (seriesCallback != null) {

            seriesLoader.loadCompleteSeries(seriesId, seriesCallback)

            currentSeries?.let {
                Toast.makeText(this, this.resources.getString(R.string.movieAdd,
                        currentSeries!!.name), Toast.LENGTH_SHORT).show()
            }

            onBackPressed()
        }


    }

    private fun showDialogIfNeeded(series: Series) {
        if (series.isInProduction) {
            val alertBuilder = AlertDialog.Builder(this)
            alertBuilder.setTitle(getString(R.string.seriesSeenHeadline, series.name))
            alertBuilder.setMessage(R.string.seriesStillInProduction)
            alertBuilder.setPositiveButton(R.string.ok) { _, _ -> moveBetweenLists(series) }
            alertBuilder.setNegativeButton(R.string.cancel) { _, _ ->
                //do nothing
            }

            alertBuilder.create().show()
        } else {
            moveBetweenLists(series)
        }
    }

    private fun moveBetweenLists(series: Series) {
        if (state == R.string.searchState) {
            seriesDbHelper.addToHistory(series)
        } else if (state == R.string.watchlistState) {
            seriesDbHelper.moveToHistory(series)
        }

        onBackPressed()
    }

    override fun onAddToWatchClicked() {
        var callback: SeriesCallback? = null

        when (state) {
            R.string.searchState -> callback = object : SeriesCallback {
                override fun onFailure() {

                }

                override fun onSuccess(series: Series) {
                    seriesDbHelper.addToWatchList(series)
                }
            }
            R.string.historyState -> seriesDbHelper.moveToWatchList(currentSeries!!)
        }

        if (callback != null) {
            seriesLoader.loadCompleteSeries(seriesId, callback)
            currentSeries?.let {
                Toast.makeText(this, this.resources.getString(R.string.movieAdd,
                        currentSeries!!.name), Toast.LENGTH_SHORT).show()
            }
        }

        onBackPressed()
    }

    override fun onItemClickListener(itemId: Long, views: Array<View>) {
        if (state != R.string.searchState) {
            val intent = Intent(this@SeriesDetailActivity, SeasonDetailActivity::class.java)
            intent.putExtra(BaseDao.SeasonEntry.COLUMN_SEASON_SERIES_ID, currentSeries!!.id)
            intent.putExtra(BaseDao.SeasonEntry.COLUMN_SEASON_SEASON_NUMBER, itemId)

            startActivity(intent)
        } else {
            val snackBar = Snackbar.make(layout,
                    R.string.notAvailableDuringSearch, Snackbar.LENGTH_SHORT)
            snackBar.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_detail)

        seriesDbHelper = SeriesDbHelper.getInstance(this)
        seriesLoader = SeriesLoader(this)

        val intent = intent
        seriesId = intent.getLongExtra(BaseDao.SeriesEntry.ID, -1)
        state = intent.getIntExtra(getString(R.string.state), -1)

        initViews()

        updateCallBack = getUpdateCallBack()
        autoUpdate()

        currentSeries = seriesDbHelper.getSeriesById(seriesId)
        if (currentSeries == null) {
            progressBar.visibility = View.VISIBLE
            fab.visibility = View.GONE
            loadRequestedSeries()
        } else {
            progressBar.visibility = View.GONE
            if( state == R.string.watchlistState)
                fab.visibility = View.VISIBLE
            assignData(currentSeries!!)
        }

        initToolbar()

        poster!!.setOnClickListener {
            val myIntent = Intent(this@SeriesDetailActivity, PosterActivity::class.java)
            myIntent.putExtra(PosterActivity.POSTER_PATH, currentSeries!!.backdropPath)
            slideOut()
            startActivity(myIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (state != R.string.searchState || currentSeries != null) {
            slideIn()
        }
    }

    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        fab = findViewById(R.id.fab)
        val layoutManager = LinearLayoutManager(this)
        poster = findViewById(R.id.movie_poster)
        layout = findViewById(R.id.overlay)
        layout.layoutManager = layoutManager
        layout.setHasFixedSize(true)

        if (state == R.string.watchlistState) {
            fab.visibility = View.VISIBLE
            fab.setOnClickListener {
                seriesDbHelper.episodeWatched(currentSeries!!)
                currentSeries = seriesDbHelper.getSeriesById(currentSeries!!.id)
                assignData(currentSeries!!)
            }
        } else {
            fab.visibility = View.GONE
        }
    }

    private fun initToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        setTitleIfNeeded()
    }

    private fun setTitleIfNeeded() {
        val collapsingToolbarLayout = findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)
        val appBarLayout = findViewById<AppBarLayout>(R.id.appbar)
        appBarLayout.addOnOffsetChangedListener(object : AppBarLayout.OnOffsetChangedListener {
            internal var isShow = true
            internal var scrollRange = -1

            override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
                if (scrollRange == -1) {
                    scrollRange = appBarLayout.totalScrollRange
                }
                if (scrollRange + verticalOffset == 0) {
                    currentSeries?.let {
                        collapsingToolbarLayout.title = currentSeries!!.name
                    }
                    isShow = true
                } else if (isShow) {
                    collapsingToolbarLayout.title = " "
                    isShow = false
                }
            }
        })
    }

    private fun autoUpdate() {
        layout.removeCallbacks(updateCallBack)
        layout.postDelayed(updateCallBack, 1000)
    }

    private fun getUpdateCallBack(): Runnable {
        return Runnable { updateSeries() }
    }

    private fun updateSeries() {
        if (state != R.string.searchState) {

            seriesLoader.loadCompleteSeries(seriesId, object : SeriesCallback {
                override fun onFailure() {
                    runOnUiThread { showNetworkError() }
                }

                override fun onSuccess(series: Series) {
                    if (currentSeries == null) {
                        return
                    }
                    series.isWatched = currentSeries!!.isWatched
                    seriesDbHelper.update(series)
                    runOnUiThread {
                        setPoster(series)
                        adapter.updateSeries(series)
                    }

                }
            })
        }
    }

    private fun assignData(series: Series) {
        setPoster(series)

        adapter = SeriesDetailAdapter(series, this, state, this, this)
        layout.adapter = adapter
    }

    private fun setPoster(series: Series) {
        val posterUri = Constants.POSTER_URI_ORIGINAL
                .replace("<posterName>", if (series.backdropPath != null)
                    series.backdropPath!!
                else
                    "/")
                .replace("<API_KEY>", getString(R.string.movieKey))
        Picasso.with(this)
                .load(posterUri)
                .error(R.drawable.placeholder_poster)
                .into(poster)
    }

    private fun loadRequestedSeries() {
        seriesLoader.loadCompleteSeries(seriesId, object : SeriesCallback {
            override fun onFailure() {

            }

            override fun onSuccess(series: Series) {
                launch(UI) {
                    currentSeries = series
                    assignData(series)
                    progressBar.visibility = View.GONE
                    if(state == R.string.watchlistState)
                        fab.visibility = View.VISIBLE

                    slideIn() }
            }
            //todo why is currentSeries still null ??
        })

    }

    private fun slideIn() {
        val animation = AnimationUtils.loadAnimation(this, R.anim.to_top)
        animation.interpolator = AccelerateDecelerateInterpolator()
        layout.startAnimation(animation)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {

            }

            override fun onAnimationEnd(animation: Animation) {
                currentSeries = seriesDbHelper.getSeriesById(seriesId)
                if (currentSeries != null) {
                    adapter.updateSeries(currentSeries!!)
                }
            }

            override fun onAnimationRepeat(animation: Animation) {

            }
        })
    }

    private fun slideOut() {
        val animation = AnimationUtils.loadAnimation(this, R.anim.to_bottom)
        animation.interpolator = AccelerateDecelerateInterpolator()
        layout.startAnimation(animation)
    }

    override fun onBackPressed() {
        val animation = AnimationUtils.loadAnimation(this, R.anim.to_bottom)
        animation.interpolator = AccelerateDecelerateInterpolator()
        layout.startAnimation(animation)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {

            }

            override fun onAnimationEnd(animation: Animation) {
                super@SeriesDetailActivity.onBackPressed()
                layout.visibility = View.GONE
            }

            override fun onAnimationRepeat(animation: Animation) {

            }
        })

    }

    private fun showNetworkError() {
        val snackbar = Snackbar
                .make(layout, R.string.noInternet, Snackbar.LENGTH_LONG)
        snackbar.show()
    }

}
