package com.optic.uberclonekotlin.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.example.easywaylocation.EasyWayLocation
import com.example.easywaylocation.Listener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.SphericalUtil
import com.optic.uberclonekotlin.R
import com.optic.uberclonekotlin.databinding.ActivityMapBinding
import com.optic.uberclonekotlin.fragments.ModalBottomSheetMenu
import com.optic.uberclonekotlin.models.Booking
import com.optic.uberclonekotlin.models.DriverLocation
import com.optic.uberclonekotlin.providers.AuthProvider
import com.optic.uberclonekotlin.providers.BookingProvider
import com.optic.uberclonekotlin.providers.ClientProvider
import com.optic.uberclonekotlin.providers.GeoProvider
import com.optic.uberclonekotlin.utils.CarMoveAnim
import org.imperiumlabs.geofirestore.callbacks.GeoQueryEventListener

class MapActivity : AppCompatActivity(), OnMapReadyCallback, Listener {
    private var googleMap: GoogleMap? = null
    private lateinit var binding: ActivityMapBinding
    private var easywayLocation: EasyWayLocation? = null
    private var myLocationLatLng: LatLng? = null
    private val geoProvider = GeoProvider()
    private val authProvider = AuthProvider()
    private val bookingProvider = BookingProvider()
    private var clientProvider = ClientProvider()

    //GOOGLE PLACES
    private var places: PlacesClient? = null
    private var autocompleteOrigin: AutocompleteSupportFragment? = null
    private var autocompleteDestination: AutocompleteSupportFragment? = null
    private var originName = ""
    private var destinationName = ""
    private var originLatLng: LatLng? = null
    private var destinationLatLng: LatLng? = null

    private var isLocationEnabled = false

    private val driverMarkers = ArrayList<Marker>()
    private val driversLocation = ArrayList<DriverLocation>()
    private val modalMenu = ModalBottomSheetMenu()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val locationRequest = LocationRequest.create().apply {
            interval = 0
            fastestInterval = 0
            priority = Priority.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = 1f
        }
        easywayLocation = EasyWayLocation(this, locationRequest, false, false, this)

        locationPermission.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))

        startGooglePlaces()
        removeBooking()
        createToken()

        binding.btnRequestTrip.setOnClickListener{ goToTripInfo() }
        binding.imageViewMenu.setOnClickListener { showModalMenu() }
    }

    val locationPermission  = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ permission ->
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            when{
                permission.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    Log.d("LOCALIZACION", "Permiso Concedido")
                    easywayLocation?.startLocation();
                }
                permission.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    Log.d("LOCALIZACION", "Permiso Concedido")
                    easywayLocation?.startLocation();
                }
                else -> {
                    Log.d("LOCALIZACION", "Permiso no concedido")
                }
            }
        }
    }

    private fun createToken() {
        clientProvider.createToken(authProvider.getId())
    }

    private fun showModalMenu() {
        modalMenu.show(supportFragmentManager, ModalBottomSheetMenu.TAG)
    }

    private fun removeBooking() {

        bookingProvider.getBooking().get().addOnSuccessListener { document ->
            if (document.exists()) {
                val booking = document.toObject(Booking::class.java)
                if (booking?.status == "create" || booking?.status == "cancel") {
                    bookingProvider.remove()
                }
            }
        }
    }

    private fun getNearbyDrivers() {

        if(myLocationLatLng == null) return

        geoProvider.getNearbyDrivers(myLocationLatLng!!, radius = 50.0).addGeoQueryEventListener(object: GeoQueryEventListener{

            override fun onKeyEntered(documentID: String, location: GeoPoint) {

                Log.d("FIRESTORE", "Documennt id: $documentID")
                Log.d("FIRESTORE", "location: $location")

                for (marker in driverMarkers) {
                    if (marker.tag != null){
                        if (marker.tag == documentID) {
                            return
                        }
                    }
                }
                //Se crea un nuevo marcador para el conductor conectado
                val driverLatLng = LatLng(location.latitude, location.longitude)
                val marker = googleMap?.addMarker(
                    MarkerOptions().position(driverLatLng).title("Conductor disponible").icon(
                        BitmapDescriptorFactory.fromResource(R.drawable.uber_car)
                    )
                )

                marker?.tag = documentID
                driverMarkers.add(marker!!)

                val dl = DriverLocation()
                dl.id = documentID
                driversLocation.add(dl)
            }

            override fun onKeyExited(documentID: String) {
                for (marker in driverMarkers) {
                    if(marker.tag != null){
                        if(marker.tag == documentID) {
                            marker.remove()
                            driverMarkers.remove(marker)
                            driversLocation.removeAt(getPositionDriver(documentID))
                            return
                        }
                    }
                }
            }

            override fun onKeyMoved(documentID: String, location: GeoPoint) {

                for (marker in driverMarkers){

                    val start = LatLng(location.latitude, location.longitude)
                    var end: LatLng? = null
                    val position = getPositionDriver(marker.tag.toString())

                    if (marker.tag != null) {
                        if (marker.tag == documentID) {
                            //marker.position = LatLng(location.latitude, location.longitude)
                            if (driversLocation[position].latlng != null) {
                                end = driversLocation[position].latlng
                            }
                            driversLocation[position].latlng = LatLng(location.latitude, location.longitude)
                            if (end != null) {
                                CarMoveAnim.carAnim(marker, end, start)
                            }
                        }
                    }
                }

            }

            override fun onGeoQueryError(exception: Exception) {

            }

            override fun onGeoQueryReady() {

            }

        })
    }

    private fun goToTripInfo(){
        if(originLatLng != null && destinationLatLng != null) {
            val i = Intent(this, TripInfoActivity::class.java)
            i.putExtra("origin", originName)
            i.putExtra("destination", destinationName)
            i.putExtra("origin_lat", originLatLng?.latitude)
            i.putExtra("origin_lng", originLatLng?.longitude)
            i.putExtra("destination_lat", destinationLatLng?.latitude)
            i.putExtra("destination_lng", destinationLatLng?.longitude)
            startActivity(i)
        }
        else{
            Toast.makeText(this, "Debes seleccionar el origen y el destino", Toast.LENGTH_LONG).show()
        }
    }

    private fun getPositionDriver(id:String): Int {
        var position = 0
        for (i in driversLocation.indices) {
            if (id == driversLocation[i].id) {
                position = i
                break
            }
        }
        return position
    }

    private fun onCameraMove() {
        googleMap?.setOnCameraIdleListener {
            try {
                val geocoder = Geocoder(this)
                originLatLng = googleMap?.cameraPosition?.target

                if (originLatLng != null){
                    val addressList = geocoder.getFromLocation(originLatLng?.latitude!!, originLatLng?.longitude!!, 1)
                    if (addressList.size > 0) {
                        val city = addressList[0].locality
                        val country = addressList[0].countryName
                        val address = addressList[0].getAddressLine(0)
                        originName = "$address $city"
                        autocompleteOrigin?.setText("$address $city")
                    }
                }
            }catch (e:Exception) {
                Log.d("ERROR", "Mensaje error: ${e.message}")
            }
        }
    }


    private fun saveLocation(){
        if (myLocationLatLng != null) {
            geoProvider.saveLocation(authProvider.getId(), myLocationLatLng!!)
        }
    }

    private fun startGooglePlaces() {
        if(!Places.isInitialized()) {
            Places.initialize(applicationContext, resources.getString(R.string.google_maps_key))
        }
        places = Places.createClient(this)
        instanceAutocompleteOrigin()
        instanceAutocompleteDestination()
    }

    private fun limitSearch() {
        val northSide = SphericalUtil.computeOffset(myLocationLatLng, 5000.0, 0.0)
        val southSide = SphericalUtil.computeOffset(myLocationLatLng, 5000.0, 180.0)

        autocompleteOrigin?.setLocationBias(RectangularBounds.newInstance(southSide, northSide))
        autocompleteDestination?.setLocationBias(RectangularBounds.newInstance(southSide, northSide))
    }

    private fun instanceAutocompleteOrigin() {
        autocompleteOrigin = supportFragmentManager.findFragmentById(R.id.placesAutocompleteOrigin) as AutocompleteSupportFragment
        autocompleteOrigin?.setPlaceFields(
            listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
            )
        )
        autocompleteOrigin?.setHint("Lugar de recogida")
        autocompleteOrigin?.setCountry("DOM")
        autocompleteOrigin?.setOnPlaceSelectedListener(object: PlaceSelectionListener{
            override fun onPlaceSelected(place: Place) {
                originName = place.name!!
                originLatLng = place.latLng
                Log.d("PLACES", "Address: $originName")
                Log.d("PLACES", "LAT: ${originLatLng?.latitude}")
                Log.d("PLACES", "LNG: ${originLatLng?.longitude}")
            }

            override fun onError(p0: Status) {
                TODO("Not yet implemented")
            }
        })
    }

    private fun instanceAutocompleteDestination() {
        autocompleteDestination = supportFragmentManager.findFragmentById(R.id.placesAutocompleteDestination) as AutocompleteSupportFragment
        autocompleteDestination?.setPlaceFields(
            listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
            )
        )
        autocompleteDestination?.setHint("Destino")
        autocompleteDestination?.setCountry("DOM")
        autocompleteDestination?.setOnPlaceSelectedListener(object: PlaceSelectionListener{
            override fun onPlaceSelected(place: Place) {
                destinationName = place.name!!
                destinationLatLng = place.latLng
                Log.d("PLACES", "Address: $destinationName")
                Log.d("PLACES", "LAT: ${destinationLatLng?.latitude}")
                Log.d("PLACES", "LNG: ${destinationLatLng?.longitude}")
            }

            override fun onError(p0: Status) {
                TODO("Not yet implemented")
            }
        })
    }


    override fun onResume() {
        super.onResume()
        easywayLocation?.startLocation();
    }

    override fun onDestroy() { // PASA CUANDO SE LA CIERRA APLICACION O PASAMOS A OTRA ACTIVIDAD
        super.onDestroy()
        easywayLocation?.endUpdates()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        onCameraMove()
        //easywayLocation?.startLocation();


        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        googleMap?.isMyLocationEnabled = false

        try {
            val success = googleMap?.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.style)
            )
            if(!success!!){
                Log.d("MAPAS", "No se pudo encontrar el estilo")
            }

        }catch (e: Resources.NotFoundException) {
            Log.d("MAPAS", "ERROR: ${e.toString()}")
        }
    }


    override fun locationOn() {

    }

    override fun currentLocation(location: Location) { // ES LA ACTUALIZACION DE LA POSICION EN TIEMPO REAL
        myLocationLatLng = LatLng(location.latitude, location.longitude) // LAT Y LONG DE LA POSICION ACTUAL

        if (!isLocationEnabled){ //Para que solamente se ejecute una vez
            isLocationEnabled = true
            googleMap?.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.builder().target(myLocationLatLng!!).zoom(15f).build()
            ))
            getNearbyDrivers()
            limitSearch()
        }
    }

    override fun locationCancelled() {

    }

}