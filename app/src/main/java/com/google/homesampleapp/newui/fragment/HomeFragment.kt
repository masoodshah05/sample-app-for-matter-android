package com.google.homesampleapp.newui.fragment

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.homesampleapp.APP_NAME
import com.google.homesampleapp.Device
import com.google.homesampleapp.R
import com.google.homesampleapp.TaskStatus
import com.google.homesampleapp.VERSION_NAME
import com.google.homesampleapp.chip.ChipClient
import com.google.homesampleapp.databinding.FragmentHomeBinding
import com.google.homesampleapp.databinding.FragmentNewDeviceBinding
import com.google.homesampleapp.databinding.HomeFragmentBinding
import com.google.homesampleapp.intentSenderToString
import com.google.homesampleapp.isMultiAdminCommissioning
import com.google.homesampleapp.lifecycle.AppLifecycleObserver
import com.google.homesampleapp.newui.activities.MainActivity
import com.google.homesampleapp.newui.adapter.DevicesListAdapter
import com.google.homesampleapp.newui.adapter.DevicesNamesAdapter
import com.google.homesampleapp.newui.model.DeviceDetails
import com.google.homesampleapp.newui.model.DevicesModel
import com.google.homesampleapp.screens.home.HomeFragment
import com.google.homesampleapp.screens.home.HomeViewModel
import com.google.homesampleapp.setDeviceTypeStrings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {
    lateinit var binding : HomeFragmentBinding
    private val viewModel: HomeViewModel by viewModels()
    @Inject
    internal lateinit var lifecycleObservers: Set<@JvmSuppressWildcards AppLifecycleObserver>
    private lateinit var commissionDeviceLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var newDeviceAlertDialog: AlertDialog
    private lateinit var newDeviceAlertDialogBinding: FragmentNewDeviceBinding
    private var deviceAttestationFailureIgnored = false
    @Inject internal lateinit var chipClient: ChipClient

    var devicesList : ArrayList<DevicesModel> = ArrayList()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HomeFragmentBinding.inflate(inflater)
        setDeviceAttestationDelegate()
        setDevicesData()
        lifecycleObservers.forEach { lifecycle.addObserver(it) }
        initContextDependentConstants()
        setData()
        binding.addRl.visibility= View.VISIBLE
        setListener()
        setUpObserver()
        commissionDeviceLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                // Commission Device Step 5.
                // The Commission Device activity in GPS (step 4) has completed.
                val resultCode = result.resultCode
                if (resultCode == Activity.RESULT_OK) {
                    Timber.d("CommissionDevice: Success")
                    // We now need to capture the device information for the app's fabric.
                    // Once this completes, a call is made to the viewModel to persist the information
                    // about that device in the app.
                    //showNewDeviceAlertDialog(result)
                    showNewDeviceAlertDialog(result)

                } else {
                    viewModel.commissionDeviceFailed(resultCode)
                }
            }
        return binding.root
    }
    private fun setDeviceAttestationDelegate() {
        chipClient.chipDeviceController.setDeviceAttestationDelegate(
            HomeFragment.DEVICE_ATTESTATION_FAILED_TIMEOUT_SECONDS
        ) { devicePtr, attestationInfo, errorCode ->
            Timber.d(
                "Device attestation errorCode: $errorCode, " +
                        "Look at 'src/credentials/attestation_verifier/DeviceAttestationVerifier.h' " +
                        "AttestationVerificationResult enum to understand the errors")

            if (errorCode == HomeFragment.STATUS_PAIRING_SUCCESS) {
                Timber.d("DeviceAttestationDelegate: Success on device attestation.")
                lifecycleScope.launch {
                    chipClient.chipDeviceController.continueCommissioning(devicePtr, true)
                }
            } else {
                Timber.d("DeviceAttestationDelegate: Error on device attestation [$errorCode].")
                // Ideally, we'd want to show a Dialog and ask the user whether the attestation
                // failure should be ignored or not.
                // Unfortunately, the GPS commissioning API is in control at this point, and the
                // Dialog will only show up after GPS gives us back control.
                // So, we simply ignore the attestation failure for now.
                // TODO: Add a new setting to control that behavior.
                deviceAttestationFailureIgnored = true
                Timber.w("Ignoring attestation failure.")
                lifecycleScope.launch {
                    chipClient.chipDeviceController.continueCommissioning(devicePtr, true)
                }
            }
        }
    }

    private fun showNewDeviceAlertDialog(activityResult: ActivityResult?) {
        newDeviceAlertDialog.setCanceledOnTouchOutside(false)

        // Set on click listener for positive button of the dialog.
        newDeviceAlertDialog.setButton(
            DialogInterface.BUTTON_POSITIVE, resources.getString(R.string.ok)) { _, _ ->
            // Extract the info entered by user and process it.
            val nameTextView: TextInputEditText = newDeviceAlertDialogBinding.nameTextView
            val deviceName = nameTextView.text.toString()
            viewModel.commissionDeviceSucceeded(activityResult!!, deviceName)
        }

        if (deviceAttestationFailureIgnored) {
            newDeviceAlertDialog.setMessage(
                Html.fromHtml(getString(R.string.device_attestation_warning),
                    Html.FROM_HTML_MODE_LEGACY
                ))
        }

        // Clear previous device name before showing the dialog
        newDeviceAlertDialogBinding.nameTextView.setText("")
        newDeviceAlertDialog.show()

        // Make the hyperlink clickable. Must be set after show().
        val msgTextView: TextView? = newDeviceAlertDialog.findViewById(android.R.id.message)
        msgTextView?.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setUpObserver() {
        viewModel.commissionDeviceIntentSender.observe(viewLifecycleOwner) { sender ->
            Timber.d(
                "commissionDeviceIntentSender.observe is called with [${intentSenderToString(sender)}]")
            if (sender != null) {
                // Commission Device Step 4: Launch the activity described in the IntentSender that
                // was returned in Step 3 (where the viewModel calls the GPS API to commission
                // the device).
                Timber.d("CommissionDevice: Launch GPS activity to commission device")
                commissionDeviceLauncher.launch(IntentSenderRequest.Builder(sender).build())
                viewModel.consumeCommissionDeviceIntentSender()
            }
        }
    }

    fun initContextDependentConstants() {
        // versionName is set in build.gradle.
        val packageInfo = requireActivity().packageManager.getPackageInfo(requireActivity().packageName, 0)
        VERSION_NAME = packageInfo.versionName
        APP_NAME = getString(R.string.app_name)
        packageInfo.packageName
        Timber.i(
            "====================================\n" +
                    "Version $VERSION_NAME\n" +
                    "App     $APP_NAME\n" +
                    "====================================")

        // Strings associated with DeviceTypes
        setDeviceTypeStrings(
            unspecified = getString(R.string.device_type_unspecified),
            light = getString(R.string.device_type_light),
            outlet = getString(R.string.device_type_outlet),
            unknown = getString(R.string.device_type_unknown))
    }

    private fun setListener() {

        binding.addRl.setOnClickListener {
            deviceAttestationFailureIgnored = false
            viewModel.stopMonitoringStateChanges()
            viewModel.commissionDevice(requireActivity() as MainActivity)
        }
        (requireActivity() as MainActivity).binding.toolbar.sensor.setOnClickListener {
            val popupWindow = showPopUp(requireActivity() as MainActivity)
            popupWindow.showAsDropDown((requireActivity() as MainActivity).binding.toolbar.sensor, 0, -7)
        }

    }

    override fun onResume() {
        super.onResume()
        val intent = requireActivity().intent
        Timber.d("onResume(): intent [${intent}]")
        if (isMultiAdminCommissioning(intent)) {
            Timber.d("Invocation: MultiAdminCommissioning")
            if (viewModel.commissionDeviceStatus.value == TaskStatus.NotStarted) {
                Timber.d("TaskStatus.NotStarted so starting commissioning")
                viewModel.multiadminCommissioning(intent, requireContext())
            } else {
                Timber.d("TaskStatus is *not* NotStarted: $viewModel.commissionDeviceStatus.value")
            }
        } else {
            Timber.d("Invocation: Main")
            viewModel.startMonitoringStateChanges()
        }
    }
    private fun setData() {
        binding.llData.visibility = View.GONE
        binding.recycler.layoutManager = LinearLayoutManager(requireActivity())
        val adapter = DevicesListAdapter(requireActivity() , devicesList , true)
        binding.recycler.adapter = adapter

    }

    private fun setDevicesData() {
        devicesList.clear()
        val deviceDetailList : ArrayList<DeviceDetails> = ArrayList()
        deviceDetailList.add(DeviceDetails("Temp" , "106" , R.color.cardCheckedColor , R.color.black))
        deviceDetailList.add(DeviceDetails("Hum" , "10%" , R.color.cardBG, R.color.black))
        deviceDetailList.add(DeviceDetails("PM2.5" , "78" , R.color.cardBG, R.color.black))
        deviceDetailList.add(DeviceDetails("CO2" , "600" , R.color.cardBG, R.color.red))
        devicesList.add(DevicesModel("Office" , deviceDetailList))
        devicesList.add(DevicesModel("Bedroom" , deviceDetailList))
        devicesList.add(DevicesModel("Living Room" , deviceDetailList))
    }

    fun showPopUp(
        context: AppCompatActivity,
    ): PopupWindow {
        val popupWindow = PopupWindow(context)


        // inflate your layout or dynamically add view
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        popupWindow.setBackgroundDrawable(
            ColorDrawable(
                Color.TRANSPARENT
            )
        )
        val view: View = inflater.inflate(R.layout.menu_layout_devices, null)
        val nameRecycler = view.findViewById<RecyclerView>(R.id.namesRecycler)
        val addCard = view.findViewById<CardView>(R.id.cardAdd)
        val showAll = view.findViewById<TextView>(R.id.showAll)

        addCard.setOnClickListener {
            viewModel.stopMonitoringStateChanges()
            viewModel.commissionDevice(requireContext())
        }
        nameRecycler.layoutManager = LinearLayoutManager(requireActivity())
        val adapter = DevicesNamesAdapter(requireActivity() , ArrayList(devicesList.map { it.deviceTitle ?:"" })){data->
            binding.addRl.visibility= View.GONE
            popupWindow.dismiss()
            (requireActivity() as MainActivity).binding.toolbar.deviceText.text = data
            binding.llData.visibility = View.VISIBLE
            val item = devicesList.filter { it.deviceTitle?.lowercase() == data.lowercase() }
            binding.recycler.layoutManager = LinearLayoutManager(requireActivity())
            val adapter = DevicesListAdapter(requireActivity() , ArrayList(item) , false)
            binding.recycler.adapter= adapter
        }
        nameRecycler.adapter = adapter

        showAll.setOnClickListener {
            setData()
            popupWindow.dismiss()
            binding.addRl.visibility= View.VISIBLE

            (requireActivity() as MainActivity).binding.toolbar.deviceText.text = "All Devices"


        }
        popupWindow.isFocusable = true
        popupWindow.width = WindowManager.LayoutParams.WRAP_CONTENT
        popupWindow.height = WindowManager.LayoutParams.WRAP_CONTENT
        popupWindow.contentView = view
        return popupWindow

    }
}