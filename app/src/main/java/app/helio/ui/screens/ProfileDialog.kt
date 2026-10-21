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
import app.helio.data.Profile
import app.helio.ui.theme.HelioColors

/** Birth year and sex drive max heart rate, VO2 max bands and estimated age. */
@Composable
fun ProfileDialog(profile: Profile, onSave: (Profile) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableStateOf(profile.birthYear?.toString() ?: "") }
    var male by remember { mutableStateOf(profile.male) }
    var height by remember { mutableStateOf(profile.heightCm?.toString() ?: "") }
    var weight by remember { mutableStateOf(profile.weightKg?.let { String.format("%.1f", it) } ?: "") }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelioColors.BgMid,
        title = { Text("Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Birth year") }, keyboardOptions = num, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(male == true, { male = true }, label = { Text("Male") })
                    FilterChip(male == false, { male = false }, label = { Text("Female") })
                }
                OutlinedTextField(height, { height = it.filter(Char::isDigit).take(3) }, label = { Text("Height (cm)") }, keyboardOptions = num, singleLine = true)
                OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, label = { Text("Weight (kg)") }, keyboardOptions = num, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Profile(year.toIntOrNull()?.takeIf { it in 1920..2020 }, male, height.toIntOrNull(), weight.toDoubleOrNull()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
