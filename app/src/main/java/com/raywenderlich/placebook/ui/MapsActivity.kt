/*
 * Copyright (c) 2020 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.raywenderlich.placebook.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.WindowManager
import android.widget.ProgressBar
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.raywenderlich.placebook.R
import com.raywenderlich.placebook.adapter.BookmarkInfoWindowAdapter
import com.raywenderlich.placebook.adapter.BookmarkListAdapter
import com.raywenderlich.placebook.viewmodel.MapsViewModel
import kotlinx.android.synthetic.main.activity_maps.*
import kotlinx.android.synthetic.main.drawer_view_maps.*
import kotlinx.android.synthetic.main.main_view_maps.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

  private lateinit var map: GoogleMap
  private lateinit var placesClient: PlacesClient
  private lateinit var fusedLocationClient: FusedLocationProviderClient
  private val mapsViewModel by viewModels<MapsViewModel>()
  private lateinit var bookmarkListAdapter: BookmarkListAdapter
  private var markers = HashMap<Long, Marker>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_maps)

    val mapFragment = supportFragmentManager
        .findFragmentById(R.id.map) as SupportMapFragment
    mapFragment.getMapAsync(this)

    setupLocationClient()
    setupToolbar()
    setupPlacesClient()
    setupNavigationDrawer()
  }

  override fun onMapReady(googleMap: GoogleMap) {
    map = googleMap

    setupMapListeners()
    createBookmarkObserver()
    getCurrentLocation()
  }

  //Creates new bookmark from a location and starts bookmark detail Activity
  private fun newBookmark(latLng: LatLng) {
    GlobalScope.launch {
      val bookmarkId = mapsViewModel.addBookmark(latLng)
      bookmarkId?.let {
        startBookmarkDetails(it)
      }
    }
  }

  private fun setupPlacesClient() {
    Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
    placesClient = Places.createClient(this);
  }

  private fun setupToolbar() {
    setSupportActionBar(toolbar)
    val toggle = ActionBarDrawerToggle(
        this,  drawerLayout, toolbar,
        R.string.open_drawer, R.string.close_drawer)
    toggle.syncState()
  }

  private fun setupMapListeners() {
    map.setInfoWindowAdapter(BookmarkInfoWindowAdapter(this))
    map.setOnPoiClickListener {
      displayPoi(it)
    }
    map.setOnInfoWindowClickListener {
      handleInfoWindowClick(it)
    }
    fab.setOnClickListener {
      searchAtCurrentLocation()
    }
    map.setOnMapLongClickListener { latLng ->
      newBookmark(latLng)
    }
  }

  private fun showProgress() {
    progressBar.visibility = ProgressBar.VISIBLE
    disableUserInteraction()
  }
  private fun hideProgress() {
    progressBar.visibility = ProgressBar.GONE
    enableUserInteraction()
  }

  private fun disableUserInteraction() {
    window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
  }

  private fun enableUserInteraction() {
    window.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
  }
  
  private fun displayPoi(pointOfInterest: PointOfInterest) {
    showProgress()
    displayPoiGetPlaceStep(pointOfInterest)
  }

  private fun displayPoiGetPlaceStep(pointOfInterest:
                                     PointOfInterest) {
    val placeId = pointOfInterest.placeId

    val placeFields = listOf(Place.Field.ID,
        Place.Field.NAME,
        Place.Field.PHONE_NUMBER,
        Place.Field.PHOTO_METADATAS,
        Place.Field.ADDRESS,
        Place.Field.LAT_LNG)

    val request = FetchPlaceRequest
        .builder(placeId, placeFields)
        .build()

    placesClient.fetchPlace(request)
        .addOnSuccessListener { response ->
      val place = response.place
      displayPoiGetPhotoStep(place)
    }.addOnFailureListener { exception ->
      if (exception is ApiException) {
        val statusCode = exception.statusCode
        Log.e(TAG,
            "Place not found: " +
                exception.message + ", " +
                "statusCode: " + statusCode)
        hideProgress()
      }
    }
  }  

  private fun displayPoiGetPhotoStep(place: Place) {
    val photoMetadata = place.getPhotoMetadatas()?.get(0)
    if (photoMetadata == null) {
      displayPoiDisplayStep(place, null)
      return
    }
    val photoRequest = FetchPhotoRequest.builder(photoMetadata)
        .setMaxWidth(resources.getDimensionPixelSize(R.dimen.default_image_width))
        .setMaxHeight(resources.getDimensionPixelSize(R.dimen.default_image_height))
        .build()
    placesClient.fetchPhoto(photoRequest).addOnSuccessListener { fetchPhotoResponse ->
      val bitmap = fetchPhotoResponse.bitmap
      displayPoiDisplayStep(place, bitmap)
    }.addOnFailureListener { exception ->
      if (exception is ApiException) {
        val statusCode = exception.statusCode
        Log.e(TAG, "Place not found: " + exception.message + ", statusCode: " + statusCode)
      }
      hideProgress()
    }
  }

  private fun displayPoiDisplayStep(place: Place, photo: Bitmap?) {
    hideProgress()
    val marker = map.addMarker(MarkerOptions()
        .position(place.latLng as LatLng)
        .title(place.name)
        .snippet(place.phoneNumber)
    )
    marker?.tag = PlaceInfo(place, photo)
    marker?.showInfoWindow()
  }
  
  override fun onRequestPermissionsResult(requestCode: Int,
                                          permissions: Array<String>,
                                          grantResults: IntArray) {
    if (requestCode == REQUEST_LOCATION) {
      if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        getCurrentLocation()
      } else {
        Log.e(TAG, "Location permission denied")
      }
    }
  }

  private fun setupLocationClient() {
    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
  }

  private fun startBookmarkDetails(bookmarkId: Long) {
    val intent = Intent(this, BookmarkDetailsActivity::class.java)
    intent.putExtra(EXTRA_BOOKMARK_ID, bookmarkId)
    startActivity(intent)
  }

  private fun handleInfoWindowClick(marker: Marker) {
    when (marker.tag) {
      is MapsActivity.PlaceInfo -> {
        val placeInfo = (marker.tag as PlaceInfo)
        if (placeInfo.place != null) {
          GlobalScope.launch {
            mapsViewModel.addBookmarkFromPlace(placeInfo.place,
                placeInfo.image)
          }
        }
        marker.remove()
      }
      is MapsViewModel.BookmarkView -> {
        val bookmarkMarkerView = (marker.tag as
            MapsViewModel.BookmarkView)
        marker.hideInfoWindow()
        bookmarkMarkerView.id?.let {
          startBookmarkDetails(it)
        }
      }
    }
  }

  //Used refactoring to update name
 private fun createBookmarkObserver() {
    mapsViewModel.getBookmarkViews()?.observe(
        this, Observer<List<MapsViewModel.BookmarkView>> {

          map.clear()
          markers.clear()

          it?.let {
            displayAllBookmarks(it)
            bookmarkListAdapter.setBookmarkData(it)
          }
        })
  }

  private fun displayAllBookmarks(
      bookmarks: List<MapsViewModel.BookmarkView>) {
    for (bookmark in bookmarks) {
      addPlaceMarker(bookmark)
    }
  }

  private fun addPlaceMarker(
      bookmark: MapsViewModel.BookmarkView): Marker? {
    val marker = map.addMarker(MarkerOptions()
            .position(bookmark.location)
            .title(bookmark.name)
            .snippet(bookmark.phone)
            .icon(bookmark.categoryResourceId?.let {
              BitmapDescriptorFactory.fromResource(it)
            })
            .alpha(0.8f))
    marker.tag = bookmark
    bookmark.id?.let { markers.put(it, marker) }
    return marker
  }

  private fun getCurrentLocation() {
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED) {
      requestLocationPermissions()
    } else {
      map.isMyLocationEnabled = true

      fusedLocationClient.lastLocation.addOnCompleteListener {
        val location = it.result
        if (location != null) {
          val latLng = LatLng(location.latitude, location.longitude)
          val update = CameraUpdateFactory.newLatLngZoom(latLng, 16.0f)
          map.moveCamera(update)
        } else {
          Log.e(TAG, "No location found")
        }
      }
    }
  }

  private fun requestLocationPermissions() {
    ActivityCompat.requestPermissions(this,
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
        REQUEST_LOCATION)
  }

  //sets up the adapter for the bookmark recycler view
  private fun setupNavigationDrawer() {
    val layoutManager = LinearLayoutManager(this)
    bookmarkRecyclerView.layoutManager = layoutManager
    bookmarkListAdapter = BookmarkListAdapter(null, this)
    bookmarkRecyclerView.adapter = bookmarkListAdapter
  }

  //This pans and zooms the map to center over a Location
  private fun updateMapToLocation(location: Location) {
    val latLng = LatLng(location.latitude, location.longitude)
    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(latLng, 16.0f))
  }

  fun moveToBookmark(bookmark: MapsViewModel.BookmarkView) {

    //Before zooming the bookmark, the navigation drawer is closed
    drawerLayout.closeDrawer(drawerView)

    //The markers HashMap is used to look up the Marker
    val marker = markers[bookmark.id]

    //If the marker is found, its Info window is shown
    marker?.showInfoWindow()

    //A Location object is created from the bookmark, and updateMapToLocation() is
    //called to zoom the map to the bookmark
    val location = Location("")
    location.latitude =  bookmark.location.latitude
    location.longitude = bookmark.location.longitude
    updateMapToLocation(location)
  }

  private fun searchAtCurrentLocation() {

    //Define fields informing Autocomplete widget what attribute to return
    val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.PHONE_NUMBER,
            Place.Field.PHOTO_METADATAS,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS,
            Place.Field.TYPES)

    //Compute bounds of currently visible region of map
    val bounds = RectangularBounds.newInstance(map.projection.visibleRegion.latLngBounds)
    try {
      //This tells the search widget to look for
      //places within the current map window before searching other areas
      val intent = Autocomplete.IntentBuilder(
              AutocompleteActivityMode.OVERLAY, placeFields)
              .setLocationBias(bounds)
              .build(this)
      // start the Activity and pass a request code of AUTOCOMPLETE_REQUEST_CODE. When
      //the user finishes the search, the results are identified by this request code
      startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
    } catch (e: GooglePlayServicesRepairableException) {
      //TODO: Handle exception
    } catch (e: GooglePlayServicesNotAvailableException) {
      //TODO: Handle exception
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int,
                                data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    // 1 check the requestCode to make sure it matches the
    //AUTOCOMPLETE_REQUEST_CODE passed into startActivityForResult().
    when (requestCode) {
      AUTOCOMPLETE_REQUEST_CODE ->
        // 2 If the resultCode indicates the user found a place, and the data is not null, then
        //you continue to process the results
        if (resultCode == Activity.RESULT_OK && data != null) {
          // 3 takes the data
          //and returns a populated Place object
          val place = Autocomplete.getPlaceFromIntent(data)
          // 4  convert the place latLng to a location and pass that to the existing
          //updateMapToLocation method
          val location = Location("")
          location.latitude = place.latLng?.latitude ?: 0.0
          location.longitude = place.latLng?.longitude ?: 0.0
          updateMapToLocation(location)
          showProgress()
          // 5 loads the place photo and displays the place Info window.
          displayPoiGetPhotoStep(place)
        }
    }
  }
 
  companion object {
    const val EXTRA_BOOKMARK_ID =
        "com.raywenderlich.placebook.EXTRA_BOOKMARK_ID"
    private const val REQUEST_LOCATION = 1
    private const val TAG = "MapsActivity"
    private const val AUTOCOMPLETE_REQUEST_CODE = 2
  }

  class PlaceInfo(val place: Place? = null, val image: Bitmap? = null)
}
