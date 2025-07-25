package com.optic.uberclonekotlin.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.easywaylocation.EasyWayLocation
import com.example.easywaylocation.Listener
import com.example.easywaylocation.draw_path.DirectionUtil
import com.example.easywaylocation.draw_path.PolyLineDataBean
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.firestore.ListenerRegistration
import com.optic.uberclonekotlin.R
import com.optic.uberclonekotlin.databinding.ActivityMapBinding
import com.optic.uberclonekotlin.databinding.ActivityMapTripBinding
import com.optic.uberclonekotlin.fragments.ModalBottomSheetTripInfo
import com.optic.uberclonekotlin.models.Booking
import com.optic.uberclonekotlin.providers.AuthProvider
import com.optic.uberclonekotlin.providers.BookingProvider
import com.optic.uberclonekotlin.providers.GeoProvider
import com.optic.uberclonekotlin.utils.CarMoveAnim

class MapTripActivity : AppCompatActivity(), OnMapReadyCallback, Listener, DirectionUtil.DirectionCallBack {
    private var listenerDriverLocation: ListenerRegistration? = null
    private var driverLocation: LatLng? = null
    private var endLatLng: LatLng? = null
    private var startLatLng: LatLng? = null

    private var listenerBooking: ListenerRegistration? = null
    private var markerDestination: Marker? = null
    private var originLatLng: LatLng? = null
    private var destinationLatLng: LatLng? = null
    private var booking: Booking? = null
    private var markerOrigin: Marker? = null
    private var bookingListener: ListenerRegistration? = null
    private var googleMap: GoogleMap? = null
    private lateinit var binding: ActivityMapTripBinding
    var easywayLocation: EasyWayLocation? = null
    private var myLocationLatLng: LatLng? = null
    private var markerDriver: Marker? = null
    private val geoProvider = GeoProvider()
    private val authProvider = AuthProvider()
    private val bookingProvider = BookingProvider()


    private var wayPoints: ArrayList<LatLng> = ArrayList()
    private val WAY_POINT_TAG = "way_point_tag"
    private lateinit var directionUtil: DirectionUtil

    private var isDriverLocationFound = false
    private var isBookingLoaded = false

    private var modalTrip = ModalBottomSheetTripInfo()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapTripBinding.inflate(layoutInflater)
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

        binding.imageViewInfo.setOnClickListener { showModalInfo() }

        locationPermission.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    val locationPermission  = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ permission ->
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            when{
                permission.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    Log.d("LOCALIZACION", "Permiso Concedido")
                }
                permission.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    Log.d("LOCALIZACION", "Permiso Concedido")
                }
                else -> {
                    Log.d("LOCALIZACION", "Permiso no concedido")
                }
            }
        }
    }

    private fun showModalInfo() {

        if (booking != null) {
            val bundle = Bundle()
            bundle.putString("booking", booking?.toJson())
            modalTrip.arguments = bundle
            modalTrip.show(supportFragmentManager, ModalBottomSheetTripInfo.TAG)
        }
        else {
            Toast.makeText(this, "No se pudo cargar la informacion", Toast.LENGTH_LONG).show()
        }
    }

    private fun getLocationDriver() {
        if(booking != null) {
            listenerDriverLocation = geoProvider.getLocationWorking(booking?.idDriver!!).addSnapshotListener{document, e ->
                if (e != null) {
                    Log.d("FIRESTORE", "ERROR: ${e.message}")
                    return@addSnapshotListener
                }

                if(driverLocation != null) {
                    endLatLng = driverLocation
                }

                if (document?.exists()!!){
                    var l = document?.get("l") as List<*>
                    val lat = l[0] as Double
                    val lng = l[1] as Double

                    driverLocation = LatLng(lat, lng)

                    if (!isDriverLocationFound && driverLocation != null) {
                        isDriverLocationFound = true
                        addDriverMarker(driverLocation!!)
                        easyDrawRoute(driverLocation!!, originLatLng!!)
                    }

                    if (endLatLng != null) {
                        CarMoveAnim.carAnim(markerDriver!!, endLatLng!!, driverLocation!!)
                    }

                    Log.d("FIRESTORE", "LOCATION $l")
                }
            }
        }
    }

    private fun getBooking() {
        listenerBooking = bookingProvider.getBooking().addSnapshotListener{document, e ->
            if (e != null) {
                Log.d("FIRESTORE", "ERROR: ${e.message}")
                return@addSnapshotListener
            }

            booking = document?.toObject(Booking::class.java)


            if (!isBookingLoaded) {
                isBookingLoaded = true
                originLatLng = LatLng(booking?.originLat!!, booking?.originLng!!)
                destinationLatLng = LatLng(booking?.destinationLat!!, booking?.destinationLng!!)
                googleMap?.moveCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.builder().target(originLatLng!!).zoom(17f).build()
                ))
                getLocationDriver()
                addOriginMarker(originLatLng!!)
            }

            if(booking?.status == "accept") {
                binding.textViewStatus.text == "Aceptado"
            }
            if(booking?.status == "started") {
                startTrip()
            }
            if(booking?.status == "finished") {
                finishTrip()
            }
        }
    }

    private fun startTrip() {
        binding.textViewStatus.text = "Iniciado"
        googleMap?.clear()
        if (driverLocation != null) {
            addDriverMarker(driverLocation!!)
            addDestinationMarker()
            easyDrawRoute(driverLocation!!, destinationLatLng!!)
        }
    }

    private fun finishTrip() {
        listenerDriverLocation?.remove()
        binding.textViewStatus.text = "Finalizado"
        val i = Intent(this, CalificationActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
    }

    private fun easyDrawRoute(originLatLng: LatLng, destinationLatLng: LatLng) {
        wayPoints.clear()
        wayPoints.add(originLatLng)
        wayPoints.add(destinationLatLng)
        directionUtil = DirectionUtil.Builder()
            .setDirectionKey(resources.getString(R.string.google_maps_key))
            .setOrigin(originLatLng)
            .setWayPoints(wayPoints)
            .setGoogleMap(googleMap!!)
            .setPolyLinePrimaryColor(R.color.black)
            .setPolyLineWidth(10)
            .setPathAnimation(true)
            .setCallback(this)
            .setDestination(destinationLatLng)
            .build()

        directionUtil.initPath()
    }

    private fun addOriginMarker(position: LatLng) {
        markerOrigin = googleMap?.addMarker(MarkerOptions().position(position).title("Recoger aqui")
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.icons_location_person)))

    }

    private fun addDriverMarker(position: LatLng) {
        markerDriver = googleMap?.addMarker(MarkerOptions().position(position).title("Tu conductor")
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.uber_car)))

    }

    private fun addDestinationMarker() {
        if(destinationLatLng != null) {
            markerDestination = googleMap?.addMarker(MarkerOptions().position(destinationLatLng!!).title("Recoger aqui")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.icons_pin)))
        }
    }

    private fun getMarketDrawable(drawable: Drawable): BitmapDescriptor {
        val canvas = Canvas()
        val bitmap = Bitmap.createBitmap(
            70,
            150,
            Bitmap.Config.ARGB_8888
        )
        canvas.setBitmap(bitmap)
        drawable.setBounds(0,0,70,150)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onDestroy() { // PASA CUANDO SE LA CIERRA APLICACION O PASAMOS A OTRA ACTIVIDAD
        super.onDestroy()
        listenerBooking?.remove()
        listenerDriverLocation?.remove()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        getBooking()

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

    }

    override fun locationCancelled() {

    }

    override fun pathFindFinish(
        polyLineDetailsMap: HashMap<String, PolyLineDataBean>,
        polyLineDetailsArray: ArrayList<PolyLineDataBean>
    ) {
        directionUtil.drawPath(WAY_POINT_TAG)
    }

}