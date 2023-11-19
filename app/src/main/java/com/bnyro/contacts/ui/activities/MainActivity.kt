package com.bnyro.contacts.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.ContactsContract.Intents
import android.provider.ContactsContract.QuickContact
import android.util.Log
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import com.bnyro.contacts.obj.ContactData
import com.bnyro.contacts.obj.ValueWithType
import com.bnyro.contacts.ui.components.dialogs.AddToContactDialog
import com.bnyro.contacts.ui.models.ContactsModel
import com.bnyro.contacts.ui.models.DialerModel
import com.bnyro.contacts.ui.models.SmsModel
import com.bnyro.contacts.ui.screens.MainAppContent
import com.bnyro.contacts.ui.theme.ConnectYouTheme
import com.bnyro.contacts.util.BackupHelper
import java.net.URLDecoder

class MainActivity : BaseActivity() {
    private val smsSendIntents = listOf(
        Intent.ACTION_VIEW,
        Intent.ACTION_SEND,
        Intent.ACTION_SENDTO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        contactsModel.initialContactId = getInitialContactId()
        contactsModel.initialContactData = getInsertContactData()
        handleVcfShareAction(contactsModel)

        smsModel = ViewModelProvider(this).get()
        smsModel?.initialAddressAndBody = getInitialSmsAddressAndBody()

        dialerModel = ViewModelProvider(this).get()
        dialerModel?.initialPhoneNumber = getInitialNumberToDial()

        setContent {
            ConnectYouTheme(themeModel.themeMode) {
                MainAppContent(smsModel!!, dialerModel!!)
                getInsertOrEditNumber()?.let {
                    AddToContactDialog(it)
                }
            }
        }
    }

    private fun getInsertContactData(): ContactData? {
        return when {
            intent?.action == Intent.ACTION_INSERT -> {
                val name = intent.getStringExtra(Intents.Insert.NAME)
                    ?: intent.getStringExtra(Intents.Insert.PHONETIC_NAME)
                ContactData(
                    displayName = name,
                    firstName = name?.split(" ")?.firstOrNull(),
                    surName = name?.split(" ", limit = 2)?.lastOrNull(),
                    organization = intent.getStringExtra(Intents.Insert.COMPANY),
                    numbers = listOfNotNull(
                        intent.getStringExtra(Intents.Insert.PHONE)?.let {
                            ValueWithType(it, 0)
                        }
                    ),
                    emails = listOfNotNull(
                        intent.getStringExtra(Intents.Insert.EMAIL)?.let {
                            ValueWithType(it, 0)
                        }
                    ),
                    notes = listOfNotNull(
                        intent.getStringExtra(Intents.Insert.NOTES)?.let {
                            ValueWithType(it, 0)
                        }
                    ),
                    addresses = listOfNotNull(
                        intent.getStringExtra(Intents.Insert.POSTAL)?.let {
                            ValueWithType(it, 0)
                        }
                    )
                )
            }

            intent?.getStringExtra("action") == "create" -> ContactData()
            else -> null
        }
    }

    private fun getInitialContactId(): Long? {
        return when (intent?.action) {
            Intent.ACTION_EDIT, Intent.ACTION_VIEW, QuickContact.ACTION_QUICK_CONTACT -> intent?.data?.lastPathSegment?.toLongOrNull()
            else -> null
        }
    }

    private fun getInsertOrEditNumber(): String? {
        return when (intent?.action) {
            Intent.ACTION_INSERT_OR_EDIT -> intent?.getStringExtra(Intents.Insert.PHONE)
            else -> null
        }
    }

    private fun getInitialSmsAddressAndBody(): Pair<String, String?>? {
        if (intent?.action !in smsSendIntents) return null

        val address = intent?.dataString
            ?.split(":")
            ?.lastOrNull()
            // the number is url encoded and hence must be decoded first
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?: return null
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT)

        return address.replace(ContactsModel.normalizeNumberRegex, "") to body
    }

    private fun getInitialNumberToDial(): String? {
        if (intent?.action != Intent.ACTION_DIAL) return null

        return intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            .takeIf { !it.isNullOrBlank() }
    }

    private fun handleVcfShareAction(contactsModel: ContactsModel) {
        if (intent?.type !in BackupHelper.vCardMimeTypes) return
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent?.data
            Intent.ACTION_SEND -> intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
            else -> null
        }

        uri?.let {
            Log.d("VCF Intent", "Received a valid intent with uri : $it")
            contactsModel.importVcf(this, it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        smsModel = null
    }

    companion object {
        var smsModel: SmsModel? = null
        var dialerModel: DialerModel? = null
    }
}
