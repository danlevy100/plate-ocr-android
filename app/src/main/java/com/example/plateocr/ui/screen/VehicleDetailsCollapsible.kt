package com.example.plateocr.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.plateocr.data.model.gov.AggregateVehicleData
import com.example.plateocr.ui.components.CollapsibleSection
import com.example.plateocr.ui.components.FieldTable
import com.example.plateocr.ui.components.MixedFieldTable
import com.example.plateocr.ui.utils.SectionFieldMapper

/**
 * Vehicle details tab with collapsible sections matching GovCar structure
 */
@Composable
fun VehicleDetailsTab(vehicleData: AggregateVehicleData?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        if (vehicleData?.vehicle == null) {
            Text(
                "No vehicle data available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            return@Column
        }

        val vehicle = vehicleData.vehicle

        // Vehicle name header (always visible) - RTL support
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Remove country from manufacturer name (same logic as getDisplayName)
                val manufacturerName = vehicle.manufacturer?.let { name ->
                    val countries = listOf(
                        "אוסטריה", "בריטניה", "ד.קוריא", "הונגריה", "סלובניה", "סלובקיה", "פורטוגל",
                        "אוסטרי", "איטליה", "אנגליה", "גרמניה", "טורקיה", "מכסיקו", "מקסיקו",
                        "קוריאה", "רומניה", "שוודיה", "תאילנד", "תורכיה",
                        "ארה״ב", "בלגיה", "ד.קור", "מרוקו", "סלובק", "סרביה", "פולין", "צ'כיה",
                        "שודיה", "תאילנ",
                        "אוסט", "ארהב", "בלגי", "גרמנ", "דרום", "הודו", "הונג", "מכסי", "סלוב",
                        "ספרד", "פולי", "פורט", "צרפת", "קנדה", "יפן", "סין", "אפ", "גר", "ספ"
                    )
                    var cleaned = name
                    for (country in countries) {
                        cleaned = cleaned
                            .removeSuffix(" $country")
                            .removeSuffix("-$country")
                    }
                    cleaned
                }

                // Main title: Manufacturer + Model
                val mainTitle = when {
                    manufacturerName != null && vehicle.model != null ->
                        "$manufacturerName ${vehicle.model}"
                    manufacturerName != null ->
                        manufacturerName
                    else ->
                        vehicle.licensePlate.toString()
                }

                Text(
                    mainTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                // Subtitle: Year + Trim Level
                val subtitleParts = mutableListOf<String>()
                vehicle.year?.let { subtitleParts.add(it.toString()) }
                vehicle.trimLevel?.let { if (it.isNotBlank()) subtitleParts.add(it) }

                if (subtitleParts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        subtitleParts.joinToString(" • "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 1: מידע בסיסי (Basic Information)
        CollapsibleSection(
            title = "מידע בסיסי",
            isLoading = vehicleData.isLoadingKmAndFlags || vehicleData.isLoadingWltpSpecs || vehicleData.isLoadingImporterPrice,
            initiallyExpanded = true
        ) {
            // Basic fields from vehicle record
            val basicFields = SectionFieldMapper.filterFields(
                vehicle.rawFields,
                SectionFieldMapper.basicInfoFields
            ).toMutableMap()

            // Add missing fields from other API sources
            // Fields like merkav, tozar, tozeret_eretz_nm may be in WLTP specs
            vehicleData.wltpSpecs?.rawFields?.let { wltpFields ->
                SectionFieldMapper.filterFields(wltpFields, SectionFieldMapper.basicInfoFields).forEach { (key, value) ->
                    if (!basicFields.containsKey(key)) {
                        basicFields[key] = value
                    }
                }
            }

            // Fields may also be in importer price
            vehicleData.importerPrice?.rawFields?.let { importerFields ->
                SectionFieldMapper.filterFields(importerFields, SectionFieldMapper.basicInfoFields).forEach { (key, value) ->
                    if (!basicFields.containsKey(key)) {
                        basicFields[key] = value
                    }
                }
            }

            // Add rishum_rishon_dt from kmAndFlags if available and not already present
            if (!basicFields.containsKey("rishum_rishon_dt")) {
                vehicleData.kmAndFlags?.rawFields?.get("rishum_rishon_dt")?.let { value ->
                    basicFields["rishum_rishon_dt"] = value
                }
            }

            if (basicFields.isNotEmpty()) {
                FieldTable(fields = basicFields)
            } else {
                Text(
                    "אין מידע זמין",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Section 2: פרטים חשובים (Important Details)
        CollapsibleSection(
            title = "פרטים חשובים",
            isLoading = vehicleData.isLoadingKmAndFlags || vehicleData.isLoadingImporterPrice || vehicleData.isLoadingDisabledTag,
            initiallyExpanded = false
        ) {
            // API Fields from vehicle
            val importantVehicleFields = SectionFieldMapper.filterFields(
                vehicle.rawFields,
                SectionFieldMapper.importantDetailsVehicleFields
            )

            // API Fields from kmAndFlags
            val kmFields = vehicleData.kmAndFlags?.rawFields?.let { raw ->
                SectionFieldMapper.filterFields(raw, SectionFieldMapper.importantDetailsKmFields)
            } ?: emptyMap()

            // API Fields from importerPrice (importer name)
            val importerFields = vehicleData.importerPrice?.rawFields?.let { raw ->
                SectionFieldMapper.filterFields(raw, setOf("shem_yevuan"))
            } ?: emptyMap()

            // API Fields from disabledTag
            val disabledTagFields = mutableMapOf<String, Any?>()
            if (vehicleData.disabledTag?.rawFields != null) {
                disabledTagFields.putAll(
                    SectionFieldMapper.filterFields(
                        vehicleData.disabledTag.rawFields,
                        SectionFieldMapper.importantDetailsDisabledTagFields
                    )
                )
            } else if (!vehicleData.isLoadingDisabledTag) {
                // If not loading and no disabled tag, explicitly add field with null to show "לא קיים"
                disabledTagFields["SUG_TOV"] = null
            }

            // Combine API fields
            val apiFields = importantVehicleFields + kmFields + importerFields + disabledTagFields

            // Custom calculated/aggregated fields
            val customFields = mutableMapOf<String, String>()

            // Yad (hand count) - number of owners minus 1, formatted as 00, 01, 02, etc.
            SectionFieldMapper.calculateYad(vehicleData)?.let { yad ->
                customFields["יד"] = yad
            }

            // MSRP from importer price
            vehicleData.getMsrp()?.let { msrp ->
                customFields["מחיר יבואן (MSRP)"] = "₪${String.format("%,d", msrp)}"
            }

            // Use value monthly
            vehicleData.getUseValueMonthly()?.let { useValue ->
                customFields["שווי שימוש חודשי"] = "₪${String.format("%,d", useValue)}"
            }

            // Recalls count
            val recallsCount = vehicleData.recalls?.size ?: 0
            if (recallsCount > 0) {
                customFields["ריקולים"] = "$recallsCount קריאות שירות"
            }

            if (apiFields.isNotEmpty() || customFields.isNotEmpty()) {
                MixedFieldTable(apiFields = apiFields, customFields = customFields)
            } else {
                Text(
                    "אין מידע זמין",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Section 3: נתוני מנוע ומכניקה (Engine & Mechanical)
        val engineLoading = vehicleData.isLoadingWltpSpecs
        val engineFields = mutableMapOf<String, Any?>()
        engineFields.putAll(SectionFieldMapper.filterFields(vehicle.rawFields, SectionFieldMapper.engineMechanicalFields))
        vehicleData.wltpSpecs?.rawFields?.let { wltp ->
            engineFields.putAll(SectionFieldMapper.filterFields(wltp, SectionFieldMapper.engineMechanicalFields))
        }

        CollapsibleSection(
            title = "נתוני מנוע ומכניקה",
            isLoading = engineLoading,
            initiallyExpanded = false
        ) {
            if (engineFields.isNotEmpty()) {
                FieldTable(fields = engineFields)
            } else {
                Text(
                    "אין מידע זמין",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Section 4: מידות ומשקלים (Dimensions & Weights)
        val dimensionsFields = mutableMapOf<String, Any?>()
        dimensionsFields.putAll(SectionFieldMapper.filterFields(vehicle.rawFields, SectionFieldMapper.dimensionsWeightsFields))
        vehicleData.wltpSpecs?.rawFields?.let { wltp ->
            dimensionsFields.putAll(SectionFieldMapper.filterFields(wltp, SectionFieldMapper.dimensionsWeightsFields))
        }

        CollapsibleSection(
            title = "מידות ומשקלים",
            isLoading = vehicleData.isLoadingWltpSpecs,
            initiallyExpanded = false
        ) {
            if (dimensionsFields.isNotEmpty()) {
                FieldTable(fields = dimensionsFields)
            } else {
                Text(
                    "אין מידע זמין",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Section 5: אבזור ופרטים נוספים (Equipment)
        val equipmentApiFields = mutableMapOf<String, Any?>()
        equipmentApiFields.putAll(SectionFieldMapper.filterFields(vehicle.rawFields, SectionFieldMapper.equipmentCountFields))
        vehicleData.wltpSpecs?.rawFields?.let { wltp ->
            equipmentApiFields.putAll(SectionFieldMapper.filterFields(wltp, SectionFieldMapper.equipmentCountFields))
            equipmentApiFields.putAll(SectionFieldMapper.filterFields(wltp, SectionFieldMapper.equipmentFeatureFields))
        }

        CollapsibleSection(
            title = "אבזור ופרטים נוספים",
            isLoading = vehicleData.isLoadingWltpSpecs || vehicleData.isLoadingModelCounts,
            initiallyExpanded = false
        ) {
            val equipmentCustomFields = mutableMapOf<String, String>()

            // Total vehicles on road
            val totalVehicles = vehicleData.getTotalVehiclesOnRoad()
            if (totalVehicles > 0) {
                equipmentCustomFields["סה״כ רכבים מדגם זה בכביש"] = String.format("%,d", totalVehicles)
            }

            if (equipmentApiFields.isNotEmpty() || equipmentCustomFields.isNotEmpty()) {
                MixedFieldTable(apiFields = equipmentApiFields, customFields = equipmentCustomFields)
            } else {
                Text(
                    "אין מידע זמין",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Section 6: בטיחות (Safety)
        val safetyFields = vehicleData.wltpSpecs?.rawFields?.let { wltp ->
            SectionFieldMapper.filterFields(wltp, SectionFieldMapper.safetyFields)
        } ?: emptyMap()

        CollapsibleSection(
            title = "בטיחות",
            isLoading = vehicleData.isLoadingWltpSpecs,
            initiallyExpanded = false
        ) {
            if (safetyFields.isNotEmpty()) {
                FieldTable(fields = safetyFields)
            } else {
                Text(
                    "אין מידע זמין",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Section 7: סביבה (Environment)
        val environmentFields = vehicleData.wltpSpecs?.rawFields?.let { wltp ->
            SectionFieldMapper.filterFields(wltp, SectionFieldMapper.environmentFields)
        } ?: emptyMap()

        CollapsibleSection(
            title = "סביבה",
            isLoading = vehicleData.isLoadingWltpSpecs,
            initiallyExpanded = false
        ) {
            if (environmentFields.isNotEmpty()) {
                FieldTable(fields = environmentFields)
            } else {
                Text(
                    "אין מידע זמין",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Section 8: היסטוריית בעלויות (Ownership History)
        CollapsibleSection(
            title = "היסטוריית בעלויות",
            isLoading = vehicleData.isLoadingOwnershipHistory,
            initiallyExpanded = false
        ) {
            val history = vehicleData.ownershipHistory
            if (history.isNullOrEmpty()) {
                Text(
                    "אין היסטוריית בעלויות במאגר הפתוח",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Column {
                    // Table header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "תאריך",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "סוג בעלות",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // Table rows
                    history.forEach { record ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                record.getFormattedDate() ?: record.ownershipDate?.toString() ?: "—",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                record.ownershipType ?: "—",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        // Section 9: לוי יצחק (Levi Itzhak)
        // Only wait for base data (step 12), adjusted price (step 13) will appear when ready
        CollapsibleSection(
            title = "לוי יצחק",
            isLoading = vehicleData.isLoadingLeviItzhak,
            initiallyExpanded = false
        ) {
            val leviData = vehicleData.leviItzhak
            if (leviData == null) {
                Text(
                    "אין מידע מלוי יצחק",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                // Create fields map from Levi Itzhak data
                val leviFields = mutableMapOf<String, Any?>()

                // Vehicle identification
                leviData.id?.let { leviFields["__levi_kod"] = it }
                leviData.getVin()?.let { leviFields["__levi_vin"] = it }

                // Vehicle details
                leviData.manufacturerName?.let { leviFields["__levi_manufacturer"] = it }
                leviData.name?.let { leviFields["__levi_model_name"] = it }
                leviData.getTrimLevelString()?.let { leviFields["__levi_trim_level"] = it }

                // Original price (base estimate without km/owners)
                leviData.getMarketPrice()?.let { leviFields["__levi_market_price_original"] = "₪${String.format("%,d", it)}" }

                // Adjusted price (calculated with actual km and owners from gov API)
                vehicleData.leviItzhakPrice?.getPrice()?.let { leviFields["__levi_market_price_adjusted"] = "₪${String.format("%,d", it)}" }

                // Debug info: show km, owners, and ownersArr used for price calculation
                vehicleData.kmAndFlags?.odometer?.let { leviFields["__levi_calc_km"] = "${String.format("%,d", it)} ק\"מ" }
                vehicleData.ownershipHistory?.count { it.ownershipType != "סוחר" }?.let { leviFields["__levi_calc_owners"] = it.toString() }
                vehicleData.leviItzhakOwnersArr?.takeIf { it.isNotEmpty() }?.let { arr ->
                    leviFields["__levi_calc_owners_arr"] = arr.joinToString(" ← ") { lineIdToHebrew(it) }
                }

                // Vehicle specs
                leviData.getColor()?.let { leviFields["__levi_color"] = it }
                leviData.engineVolume?.let { leviFields["__levi_engine_volume"] = "${String.format("%,d", it)} סמ\"ק" }
                leviData.weight?.let { leviFields["__levi_weight"] = "${String.format("%,d", it)} ק\"ג" }

                // Test and ownership info
                leviData.getLastTestKm()?.let { leviFields["__levi_last_test_km"] = "${String.format("%,d", it)} ק\"מ" }
                leviData.getCurrentOwnerType()?.let { leviFields["__levi_current_owner"] = it }
                leviData.aliyaDate?.let { leviFields["__levi_aliya_date"] = it }

                // Ownership history summary
                leviData.ownershipHistory?.let { history ->
                    if (history.isNotEmpty()) {
                        val historyStr = history.mapNotNull { entry ->
                            val date = entry.getFormattedDate() ?: entry.date
                            val type = entry.type
                            if (date != null && type != null) "$date: $type" else null
                        }.joinToString(" ← ")
                        if (historyStr.isNotEmpty()) {
                            leviFields["__levi_ownership_history"] = historyStr
                        }
                    }
                }

                if (leviFields.isNotEmpty()) {
                    MixedFieldTable(apiFields = emptyMap(), customFields = leviFields.mapValues { it.value.toString() })
                } else {
                    Text(
                        "אין מידע זמין",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Loading indicator at bottom
        if (!vehicleData.isFullyLoaded()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "טוען נתונים נוספים... (${vehicleData.getLoadedSectionsCount()}/13 מקורות)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/**
 * Convert Levi Itzhak lineID string to Hebrew ownership type.
 * e.g., "0-2" -> "פרטי", "35-2-12" -> "חברה"
 */
private fun lineIdToHebrew(lineIdString: String): String {
    // Extract lineID from format "lineID-2" or "lineID-2-time"
    val lineId = lineIdString.split("-").firstOrNull()?.toIntOrNull() ?: return lineIdString

    return when (lineId) {
        0 -> "פרטי"
        26 -> "השכרה"
        35 -> "חברה"
        99 -> "ליסינג פרטי"
        62 -> "ליסינג לחברה"
        90 -> "ליסינג מימוני"
        91 -> "ליסינג מימוני לחברה"
        30 -> "ביה\"ס נהיגה"
        31 -> "מונית"
        38 -> "קיבוץ/צה\"ל"
        41 -> "עירייה/ממשלה"
        else -> lineIdString
    }
}
