package org.who.ddccverifier

import android.os.Bundle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import org.who.ddccverifier.databinding.ActivityMainBinding
import org.who.ddccverifier.services.trust.TrustRegistrySingleton
import org.who.ddccverifier.trust.TrustRegistry

/**
 * Screen / Class flow:
 *
 * ┌──────────────────────────────────────────────────┐      ┌────────────────┐ ┌──────────┐
 * │                  MainActivity                    │      │ TrustRegistry  ├↔┤ KeyUtils │
 * └──────────────────────────────────────────────────┘      └─────────────╥──┘ └──────────┘
 * ┌──────────────┐ ┌──────────────┐ ┌────────────────┐                    ║
 * │ HomeFragment ├→┤ ScanFragment ├→┤ ResultFragment │←─DDCC UI Card──────╫─────────┐
 * └──────────────┘ └─────┬──▲─────┘ └────────┬───────┘                    ║         │
 *                   Image│  │QRContent       │QRContent                   ║         │
 *                  ┌─────▼──┴─────┐     ┌────▼───────┐                    ║         │
 *                  │   QRFinder   │     │ QRDecoder  │                    ║         │
 *                  └──────────────┘     └────┬───────┘                    ║         │
 *                                            │QRContent                   ║         │
 *             ┌─────────────────┬────────────┴─────┬───────────────────┐  ║         │
 *  ╔══════════╪═════════════════╪══════════════════╪═══════════════════╪══╩══════╗  │
 *  ║ ┌────────▼───────┐  ┌──────▼──────┐   ┌───────▼───────┐   ┌───────▼───────┐ ║  │
 *  ║ │  HCertVerifier │  │ SHCVerifier │   │ DivocVerifier │   │ ICAOVerifier  │ ║  │
 *  ║ └────┬───────────┘  └──────┬──────┘   └───────┬───────┘   └───────┬───────┘ ║  │
 *  ╚══════╪═════════════════════╪══════════════════╪═══════════════════╪═════════╝  │
 *         │HCERT CBOR           │JWT               │JSONLD             │iJSON       │
 *    ┌────▼───────────┐ ┌───────▼───────┐ ┌────────▼─────────┐ ┌───────▼─────────┐  │
 *    │ CBORTranslator │ │ JWTTranslator │ │ JSONLDTranslator │ │ IJsonTranslator │  │
 *    └──┬──────────┬──┘ └───────┬───────┘ └────────┬─────────┘ └───────┬─────────┘  │
 *   FHIR│Struct    │DCC CWT     │FHIR DDCC         │FHIR DDCC          │FHIR DDCC   │
 * ┌─────▼────┐┌────▼─────┐      │                  │                   │            │
 * │ WHO2FHIR ││ DCC2FHIR │      │                  │                   │            │
 * └─────┬────┘└────┬─────┘      │                  │                   │            │
 *   FHIR│DDCC  FHIR│DDCC        │                  │                   │            │
 *       └──────────┴────────────┴────────────┬─────┴───────────────────┘            │
 *                                            │                                      │
 *  ┌──────────────┐                          │DDCC Composite                        │
 *  │ Assets       │ ┌────────────────┐  ┌────▼───────────┐                          │
 *  │ - ModelInfo  ├↔┤CQLLibraryLoader├←→┤ CQLEvaluator   │                          │
 *  │ - FHIRHelper │ └────────────────┘  └────┬───────────┘                          │
 *  │ - DDCCPass   │                          │DDCC Composite                        │
 *  └──────────────┘                     ┌────▼───────────┐                          │
 *                                       │ DDCCFormatter  ├→─DDCC UI Card────────────┘
 *                                       └────────────────┘
 */
class MainActivity : AuthActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private lateinit var mMenu: Menu

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onAccountState(isReady: Boolean, isAuthorized: Boolean) {
        if (!this::mMenu.isInitialized) return
        mMenu.findItem(R.id.action_sign_in).isEnabled = isReady
        mMenu.findItem(R.id.action_logout).isEnabled = isReady
        mMenu.findItem(R.id.action_sign_in).isVisible = !isAuthorized
        mMenu.findItem(R.id.action_logout).isVisible = isAuthorized
    }

    override fun onNewUserInfo(userInfo: User) {
        Toast.makeText(this, "Welcome " + userInfo.name, Toast.LENGTH_SHORT).show()
    }

    override fun backgroundInit() {
        super.backgroundInit()
        // Triggers Networking
        TrustRegistrySingleton.init()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        mMenu = menu
        updateAccountState()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_sign_in -> { requestAuthorization(); return true; }
            R.id.action_logout -> { requestSignOff(); return true; }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}