package com.example.plateocr.data.model.gov

/**
 * EU Type Approval information.
 * Technical certification data for vehicle type.
 *
 * Data sources:
 * - Resource A: 7cb2bd95-bf2e-49b6-aea1-fcb5ff6f0473
 * - Resource B: 786b33b5-75c4-42a3-a241-b1af3c9ca487
 */
data class EuTypeApproval(
    val rawFields: Map<String, Any?>,
    val licensePlate: Long?,
    val typeApprovalNumber: String?,
    val variant: String?,
    val version: String?
)
