package com.example.test1

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.test1.ui.theme.Test1Theme
import com.google.android.filament.Engine
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.exceptions.UnavailableException
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Rotation
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import java.util.concurrent.TimeUnit

private const val kModelFile = "models/direction_arrow.glb"
private const val kMaxModelInstances = 10
lateinit var session: Session
private lateinit var sensors: Sensors
private const val FINE_LOCATION_REQUEST_CODE = 1001
private lateinit var fusedLocationClient: FusedLocationProviderClient

data class Waypoint(
    var latitude: Double,
    val longitude: Double,
    val altitude: Double
)
class MainActivity : AppCompatActivity() {
    private val deviceBearing = mutableStateOf(0.0)
    private val cur_latitude = mutableStateOf(0.0)
    private val cur_longitude = mutableStateOf(0.0)
    private val cur_altitude = mutableStateOf(0.0)
    var hasInstantiated = mutableStateOf(false)
    private var allWaypoints = mutableListOf<Waypoint>()
    private var bearing = mutableStateOf(0.0)
    private lateinit var wifiManager: WiFiManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensors = Sensors(sensorManager, deviceBearing)
        wifiManager = WiFiManager(this)
        setContent {
            Test1Theme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    if(allWaypoints.size == 0){
                        var tempWaypoint1: Waypoint = Waypoint(1.433279, 103.7875715, 50.9)
                        var tempWaypoint2: Waypoint = Waypoint(1.433287, 103.7875579, 50.9)
                        allWaypoints.add(tempWaypoint1)
                        allWaypoints.add(tempWaypoint2)
                    }
                    // Check if ARCore is supported and up-to-date
                    if (isARCoreSupportedAndUpToDate()) {
                        // Request the ACCESS_FINE_LOCATION permission if not granted
                        if (hasFineLocationPermission()) {
                            // Permission granted, create the ARCore session
                            createSession()
                            // The destroy calls are automatically made when their disposable effect leaves
                            // the composition or its key changes.
                            val engine = rememberEngine()
                            val modelLoader = rememberModelLoader(engine)
                            val materialLoader = rememberMaterialLoader(engine)
                            val cameraNode = rememberARCameraNode(engine)
                            val childNodes = rememberNodes()
                            val view = rememberView(engine)
                            val collisionSystem = rememberCollisionSystem(view)

                            var planeRenderer by remember { mutableStateOf(true) }

                            val modelInstances = remember { mutableListOf<ModelInstance>() }
                            var trackingFailureReason by remember {
                                mutableStateOf<TrackingFailureReason?>(null)
                            }
                            var frame by remember { mutableStateOf<Frame?>(null) }

                            ARScene(
                                modifier = Modifier.fillMaxSize(),
                                childNodes = childNodes,
                                engine = engine,
                                view = view,
                                modelLoader = modelLoader,
                                collisionSystem = collisionSystem,
                                sessionConfiguration = { session, config ->
                                    config.depthMode =
                                        when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                                            true -> Config.DepthMode.AUTOMATIC
                                            else -> Config.DepthMode.DISABLED
                                        }
                                    config.instantPlacementMode =
                                        Config.InstantPlacementMode.LOCAL_Y_UP
                                    config.lightEstimationMode =
                                        Config.LightEstimationMode.ENVIRONMENTAL_HDR
                                },
                                cameraNode = cameraNode,
                                planeRenderer = planeRenderer,
                                onTrackingFailureChanged = {
                                    trackingFailureReason = it
                                },
                                onSessionUpdated = { session, updatedFrame ->
                                    frame = updatedFrame


                                    if (childNodes.isEmpty()) {
                                        updatedFrame.getUpdatedPlanes()
                                            .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                                            ?.let { it.createAnchorOrNull(it.centerPose) }
                                            ?.let { anchor ->
                                                childNodes += createAnchorNode(
                                                    engine = engine,
                                                    modelLoader = modelLoader,
                                                    materialLoader = materialLoader,
                                                    modelInstances = modelInstances,
                                                    anchor = anchor,
                                                    rotationDegrees = 0.0f
                                                )
                                            }
                                    }

//                                    if(bearing.value != 0.0){
//
//                                        updatedFrame.getUpdatedPlanes()
//                                            .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
//                                            ?.let { it.createAnchorOrNull(it.centerPose) }
//                                            ?.let { anchor ->
//                                                childNodes += createAnchorNode(
//                                                    engine = engine,
//                                                    modelLoader = modelLoader,
//                                                    materialLoader = materialLoader,
//                                                    modelInstances = modelInstances,
//                                                    anchor = anchor,
//                                                    rotationDegrees = bearing.value.toFloat()
//                                                )
//                                            }
//
//
//                                    }
                                },
                                onGestureListener = rememberOnGestureListener(
                                    onSingleTapConfirmed = { motionEvent, node ->
                                        if (node == null) {
                                            val hitResults =
                                                frame?.hitTest(motionEvent.x, motionEvent.y)
                                            hitResults?.firstOrNull {
                                                it.isValid(
                                                    depthPoint = false,
                                                    point = false
                                                )
                                            }?.createAnchorOrNull()
                                                ?.let { anchor ->
                                                    planeRenderer = false
                                                    childNodes += createAnchorNode(
                                                        engine = engine,
                                                        modelLoader = modelLoader,
                                                        materialLoader = materialLoader,
                                                        modelInstances = modelInstances,
                                                        anchor = anchor,
                                                        rotationDegrees = bearing.value.toFloat()
                                                    )
                                                }
                                        }
                                    })
                            )
                            Text(
                                modifier = Modifier
                                    .systemBarsPadding()
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, start = 32.dp, end = 32.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 28.sp,
                                color = Color.White,
                                text = trackingFailureReason?.let {
                                    it.getDescription(LocalContext.current)
                                } ?: if (childNodes.isEmpty()) {
                                    stringResource(R.string.point_your_phone_down)
                                } else {
                                    stringResource(R.string.tap_anywhere_to_add_model)
                                }
                            )

                            Log.d("Child Nodes Count", childNodes.size.toString())
                            var index = 0
                            for (i in childNodes) {
                                Log.d("Child Node Index", (index++).toString())
                                Log.d("Child Nodes XYZ", i.worldPosition.xyz.toString())
                            }
                            Log.d("Location", "Session Created!")
                        } else {
                            // Permission not granted, request it
                            requestFineLocationPermission()
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter // Align content to the bottom center
                    ) {
                        Column(

//                        contentAlignment = Alignment.BottomCenter // Align content to the bottom center
                        ) {
//                        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                            Box(

                            ) {
                                Column(
                                    modifier = Modifier.padding(bottom = 16.dp) // Add padding to the bottom
                                ) {
                                    Spacer(modifier = Modifier.height(16.dp)) // Add space above the Column


                                    if(allWaypoints.size > 0) {
                                        Text("Lat: ${allWaypoints[allWaypoints.size - 1].latitude.toString()}")
                                        Text("Long: ${allWaypoints[allWaypoints.size - 1].longitude.toString()}")
                                        Text(
                                            "Altitude: ${
                                                allWaypoints[allWaypoints.size - 1].altitude.toString()
                                            }"
                                        )
                                        if(allWaypoints.size > 1) {
                                            calculateAngleToTrueNorth(
                                                allWaypoints[allWaypoints.size - 2].latitude,
                                                allWaypoints[allWaypoints.size - 2].longitude,
                                                allWaypoints[allWaypoints.size - 1].latitude,
                                                allWaypoints[allWaypoints.size - 1].longitude
                                            )
                                            Text("Bearingg: ${bearing.value}")
                                        }
                                    }
                                }
                            }
//                            Text(text = "Device: ${deviceBearing.value.toString()}")
                            Button(
                                onClick = {
                                    Log.d("Location", "Fetching location!")
                                    updateText()
                                },
                                content = { Text("Get Location") }
                            )
                        }

                    }
                }
            }
        }
        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Request location updates
        requestLocationUpdates()
        Log.d("WiFi", "performing scan!")
        performWiFiScan()
    }
    fun calculateAngleToTrueNorth(latA: Double, lonA: Double, latB: Double, lonB: Double): Double {
        // Calculate initial bearing from classroom A to classroom B
        val bearingAB = calculateBearing(latA, lonA, latB, lonB)

        // Adjust for magnetic declination (+0.05° for Singapore)
        val declination = 0.05 // Singapore's declination value
        val angleToTrueNorth = (bearingAB + declination) % 360.0
        bearing.value = angleToTrueNorth
        return angleToTrueNorth
    }

    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2))
        val x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
                Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon)
        var bearing = Math.toDegrees(Math.atan2(y, x))
        bearing = (bearing + 360) % 360
        return bearing
    }
    private fun updateText(){
        var tempWaypoint: Waypoint = Waypoint(cur_latitude.value, cur_longitude.value, cur_altitude.value)

//        allWaypoints.add(tempWaypoint)
        Log.d("GPS", allWaypoints.size.toString())
        Log.d("GPS", allWaypoints[allWaypoints.size-1].toString())

    }
    // Function to check if ACCESS_FINE_LOCATION permission is granted
    private fun hasFineLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Function to request ACCESS_FINE_LOCATION permission
    private fun requestFineLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            FINE_LOCATION_REQUEST_CODE
        )
    }

    // Handle permission result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == FINE_LOCATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, create the ARCore session
                createSession()
            } else {
                // Permission denied, handle accordingly
                // You may want to show a message to the user explaining why the permission is needed
                Log.e("Location", "Fine location permission denied.")
            }
        }
        if (requestCode == REQUEST_FINE_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, perform WiFi scan
//                Log.d("WiFi", "performing scan!")
                performWiFiScan()
            } else {
                // Permission denied, handle accordingly
                // You may display a message to the user indicating why the permission is required
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        // Release native heap memory used by an ARCore session.
        session.close()
    }


    fun createSession() {
        // Create a new ARCore session.
        session = Session(this)

        // Create a session config.
        val config = Config(session)

        // Enable Geospatial mode if supported
        val isGeospatialModeSupported = session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)
        if (isGeospatialModeSupported) {
            config.geospatialMode = Config.GeospatialMode.ENABLED
            Log.d("Geospatial", "Geospatial enabled.")
        }

        // Check whether the user's device supports the Depth API.
        val isDepthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        if (isDepthSupported) {
            config.depthMode = Config.DepthMode.AUTOMATIC
        }
        // Configure the session.
        session.configure(config)
    }

    private fun performWiFiScan() {
        // Example usage: scan WiFi networks
        val scanResults = wifiManager.scanWifiNetworks()
        for (scanResult in scanResults) {
            // Check if the SSID matches the current Wi-Fi connection SSID


            // Retrieve and log only the SSID
            val level = scanResult.level
            val ssid = scanResult.SSID
            Log.d("WiFiManagerTest", scanResult.toString())

        }


    }

    companion object {
        private const val REQUEST_FINE_LOCATION_PERMISSION = 123
    }
    private fun requestLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("Location", "Location permission not granted")
            return
        }


        fun createLocationRequest() = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.MINUTES.toMillis(2)
        ).apply {
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
//            setDurationMillis(TimeUnit.MINUTES.toMillis(5))
            setIntervalMillis(200)
            setWaitForAccurateLocation(true)
            setMaxUpdates(Integer.MAX_VALUE)
        }.build()

        val locationRequest = createLocationRequest()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null
        )


    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult ?: return
            for (location in locationResult.locations) {
                // Handle location updates here
                Log.d("Location", "Lat: ${location.latitude}, Long: ${location.longitude}, " +
                        "HasAltitude: ${location.hasAltitude().toString()}, Altitude: ${location.altitude}")

                cur_latitude.value = location.latitude
                cur_longitude.value = location.longitude
                cur_altitude.value = location.altitude
            }
        }
    }

    // Verify that ARCore is installed and using the current version.
    private fun isARCoreSupportedAndUpToDate(): Boolean {
        return when (ArCoreApk.getInstance().checkAvailability(this)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> true
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD, ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                try {
                    // Request ARCore installation or update if needed.
                    when (ArCoreApk.getInstance().requestInstall(this, true)) {
                        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                            Log.i(ContentValues.TAG, "ARCore installation requested.")
                            false
                        }

                        ArCoreApk.InstallStatus.INSTALLED -> true
                    }
                } catch (e: UnavailableException) {
                    Log.e(ContentValues.TAG, "ARCore not installed", e)
                    false
                }
            }

            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE ->
                // This device is not supported for AR.
                false

            ArCoreApk.Availability.UNKNOWN_CHECKING -> {
                // ARCore is checking the availability with a remote query.
                // This function should be called again after waiting 200 ms to determine the query result.
                Handler(Looper.getMainLooper()).postDelayed({
                    isARCoreSupportedAndUpToDate() // Call the function again after a delay
                }, 200)
            }

            ArCoreApk.Availability.UNKNOWN_ERROR, ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> {
                // There was an error checking for AR availability. This may be due to the device being offline.
                // Handle the error appropriately.
                Log.e(ContentValues.TAG, "Error checking ARCore availability")
                // Display an error message or take appropriate action
                false
            }
        }
    }
    fun createAnchorNode(
        engine: Engine,
        modelLoader: ModelLoader,
        materialLoader: MaterialLoader,
        modelInstances: MutableList<ModelInstance>,
        anchor: Anchor,
        rotationDegrees: Float
    ): AnchorNode {
        val anchorNode = AnchorNode(engine = engine, anchor = anchor)
        val modelNode = ModelNode(
            modelInstance = modelInstances.apply {
                if (isEmpty()) {
                    this += modelLoader.createInstancedModel(kModelFile, kMaxModelInstances)
                }
            }.removeLast(),
            // Scale to fit in a 0.5 meters cube
            scaleToUnits = 0.5f
        ).apply {
            // Model Node needs to be editable for independent rotation from the anchor rotation
            isEditable = true
            rotation = Rotation(0.0f, rotationDegrees, 0.0f)
        }
        val boundingBoxNode = CubeNode(
            engine,
            size = modelNode.extents,
            center = modelNode.center,
            materialInstance = materialLoader.createColorInstance(Color.White.copy(alpha = 0.5f))
        ).apply {
            isVisible = false
        }
        modelNode.addChildNode(boundingBoxNode)
        anchorNode.addChildNode(modelNode)



        listOf(modelNode, anchorNode).forEach {
            it.onEditingChanged = { editingTransforms ->
                boundingBoxNode.isVisible = editingTransforms.isNotEmpty()
            }
        }
        return anchorNode
    }
}




@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Test1Theme {
        Greeting("Android")
    }
}