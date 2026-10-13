package de.moba.core.runtime

/**
 * Oeffentlicher Schluessel des Herstellers (ECDSA P-256, X.509, Base64). Die App prueft damit Lizenzschluessel.
 * Der passende private Schluessel liegt NICHT im Repository; wer ihn hat, kann Lizenzen erstellen
 * (siehe tools/license). Ein neues Schluesselpaar macht alle bisher verkauften Lizenzen ungueltig.
 */
object ManufacturerKey {
    const val PUBLIC = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAELwDGBgrpJ9EXv1emHh0SeRy0sY2I4gPmv5j7gk37W17BZ7R3CRvuqOz/ugnzo4BZuTRBSRSI+LZUsCqegBTxCQ=="
}
