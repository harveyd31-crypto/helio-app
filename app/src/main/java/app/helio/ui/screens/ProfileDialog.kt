package app.helio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.helio.data.Profile
import app.helio.ui.theme.HelioColors

/** Birth year and sex drive max heart rate, VO2 max bands and estimated age. */
@Composable
fun ProfileDialog(profile: Profile, strapMac: String, strapKey: String, onSave: (Profile, String, String) -> String?, onDismiss: () -> Unit, importStatus: String? = null, onImport: () -> Unit = {}) {
    var year by remember { mutableStateOf(profile.birthYear?.toString() ?: "") }
    var male by remember { mutableStateOf(profile.male) }
    var height by remember { mutableStateOf(profile.heightCm?.toString() ?: "") }
    var weight by remember { mutableStateOf(profile.weightKg?.let { String.format("%.1f", it) } ?: "") }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    var mac by remember { mutableStateOf(strapMac) }
    var key by remember { mutableStateOf(strapKey) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelioColors.BgMid,
        title = { Text("Profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Birth year") }, keyboardOptions = num, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(male == true, { male = true }, label = { Text("Male") })
                    FilterChip(male == false, { male = false }, label = { Text("Female") })
                }
                OutlinedTextField(height, { height = it.filter(Char::isDigit).take(3) }, label = { Text("Height (cm)") }, keyboardOptions = num, singleLine = true)
                OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, label = { Text("Weight (kg)") }, keyboardOptions = num, singleLine = true)
                Text("HELIO STRAP · DIRECT", style = app.helio.ui.theme.cap(11.sp, HelioColors.Strain))
                Text("Syncs everything straight from the strap, no Zepp needed. Get the MAC and auth key once with Ridge's keyfetch script (or Gadgetbridge's export). Leave empty to use Health Connect.",
                    style = app.helio.ui.theme.body(12.sp, HelioColors.TextMuted))
                OutlinedTextField(mac, { mac = it.take(17) }, label = { Text("Strap MAC (C4:A1:…)") }, singleLine = true)
                OutlinedTextField(key, { key = it.take(40) }, label = { Text("Auth key (32 hex)") }, singleLine = true)
                error?.let { Text(it, style = app.helio.ui.theme.body(12.sp, HelioColors.RecRed)) }
                Text("ZEPP HISTORY", style = app.helio.ui.theme.cap(11.sp, HelioColors.Strain))
                Text("Bring in your older months from the Zepp cloud (heart rate, steps, stress, sleep). Make the file on your Mac with tools/zepp-history, then pick it here. It only fills gaps.",
                    style = app.helio.ui.theme.body(12.sp, HelioColors.TextMuted))
                androidx.compose.material3.OutlinedButton(onClick = onImport) { Text("Import Zepp history file") }
                importStatus?.let { Text(it, style = app.helio.ui.theme.body(12.sp, HelioColors.TextSoft)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = onSave(Profile(year.toIntOrNull()?.takeIf { it in 1920..2020 }, male, height.toIntOrNull(), weight.toDoubleOrNull()), mac, key)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
