package id.tege.video.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationStampTest {
    private val good = GeoFix(-3.28914, 114.01423, 8f, 3_000L)
    private val all = StampFields()

    @Test fun thereAreEightTemplatesAndNextWrapsAround() {
        assertEquals(8, StampTemplate.values().size)
        assertEquals(StampTemplate.KARTU, StampTemplate.POLOS.next())
        assertEquals(StampTemplate.POLOS, StampTemplate.STEMPEL.next())
        var t = StampTemplate.POLOS
        repeat(8) { t = t.next() }
        assertEquals(StampTemplate.POLOS, t)
    }

    @Test fun unknownTemplateNameFallsBackToKartu() {
        assertEquals(StampTemplate.KARTU, StampTemplate.of(null))
        assertEquals(StampTemplate.KARTU, StampTemplate.of("HILANG"))
        assertEquals(StampTemplate.POSTER, StampTemplate.of("POSTER"))
    }

    @Test fun stateFollowsPermissionProviderAndSignal() {
        assertEquals(LocationState.NO_PERMISSION, LocationStatus.classify(false, true, good))
        assertEquals(LocationState.LOCATION_OFF, LocationStatus.classify(true, false, null))
        assertEquals(LocationState.SEARCHING, LocationStatus.classify(true, true, null))
        assertEquals(LocationState.GOOD, LocationStatus.classify(true, true, good))
        assertEquals(LocationState.WEAK, LocationStatus.classify(true, true, good.copy(accuracyM = 120f)))
        assertEquals(LocationState.STALE, LocationStatus.classify(true, true, good.copy(ageMs = 300_000L)))
        // Exactly at the limits is still fine.
        assertEquals(LocationState.GOOD, LocationStatus.classify(true, true, good.copy(accuracyM = 50f)))
        assertEquals(LocationState.GOOD, LocationStatus.classify(true, true, good.copy(ageMs = 120_000L)))
        // Unknown accuracy is not called weak.
        assertEquals(LocationState.GOOD, LocationStatus.classify(true, true, good.copy(accuracyM = null)))
    }

    @Test fun messagesNameTheLimitation() {
        assertNull(LocationStatus.message(LocationState.GOOD, good))
        assertTrue(LocationStatus.message(LocationState.WEAK, good.copy(accuracyM = 120f))!!.contains("±120 m"))
        assertTrue(LocationStatus.message(LocationState.STALE, good.copy(ageMs = 300_000L))!!.contains("5 menit"))
        assertTrue(LocationStatus.message(LocationState.NO_PERMISSION, null)!!.contains("Izin lokasi"))
        assertTrue(LocationStatus.message(LocationState.LOCATION_OFF, null)!!.contains("Lokasi HP mati"))
        assertTrue(LocationStatus.message(LocationState.SEARCHING, null)!!.contains("Mencari"))
    }

    @Test fun bannerPrefersSignalProblemThenInternetLimit() {
        assertEquals(LocationStatus.message(LocationState.SEARCHING, null),
            LocationStatus.banner(LocationState.SEARCHING, null, all, true))
        assertTrue(LocationStatus.banner(LocationState.GOOD, good, all, true)!!.contains("butuh internet"))
        assertNull(LocationStatus.banner(LocationState.GOOD, good, all, false))
        // Place name not wanted: the internet limit does not matter.
        assertNull(LocationStatus.banner(LocationState.GOOD, good, all.copy(place = false), true))
    }

    @Test fun notifiesOnChangeAndOnlyAnnouncesRecoveryAfterAProblem() {
        assertFalse(LocationStatus.shouldNotify(null, LocationState.GOOD))
        assertTrue(LocationStatus.shouldNotify(null, LocationState.SEARCHING))
        assertFalse(LocationStatus.shouldNotify(LocationState.WEAK, LocationState.WEAK))
        assertTrue(LocationStatus.shouldNotify(LocationState.WEAK, LocationState.GOOD))
        assertTrue(LocationStatus.shouldNotify(LocationState.GOOD, LocationState.WEAK))
    }

    @Test fun placeNameIsShortAndSkipsUnnamedRoads() {
        assertEquals("Jl. Tjilik Riwut, Maliku, Pulang Pisau",
            PlaceName.build("Jl. Tjilik Riwut", "Maliku", "Kecamatan Maliku", "Pulang Pisau", "Kalimantan Tengah"))
        assertEquals("Maliku, Kalimantan Tengah", PlaceName.build("Unnamed Road", null, "Maliku", null, "Kalimantan Tengah"))
        assertEquals("Maliku", PlaceName.build(null, "Maliku", "Maliku", null, null))
        assertNull(PlaceName.build(" ", null, "", null, null))
    }

    @Test fun contentWithGoodSignalHasAllLines() {
        val c = StampText.content(all, LocationState.GOOD, good, "Jl. Tjilik Riwut, Maliku", "3 Nov 2026", "08.15 WIB")
        assertEquals("Jl. Tjilik Riwut, Maliku", c.place)
        assertEquals("-3.2891, 114.0142", c.coords)
        assertEquals("3 Nov 2026", c.date)
        assertEquals("08.15 WIB", c.time)
        assertNull(c.note)
        assertEquals("Jl. Tjilik Riwut", c.headline)
        assertEquals("Maliku", c.subline)
        assertEquals("3 Nov 2026 · 08.15 WIB", c.dateTime)
    }

    @Test fun contentUsesOnlyTheChosenFields() {
        val c = StampText.content(StampFields(place = false, coords = true, date = false, time = true), LocationState.GOOD, good, "Maliku", "3 Nov 2026", "08.15 WIB")
        assertNull(c.place)
        assertEquals("-3.2891, 114.0142", c.coords)
        assertNull(c.date)
        assertEquals("08.15 WIB", c.time)
        assertTrue(StampText.content(StampFields(false, false, false, false), LocationState.GOOD, good, "Maliku", "d", "t").isEmpty)
    }

    @Test fun withoutPlaceNameTheCoordinatesStandInWithoutRepeating() {
        val c = StampText.content(all, LocationState.GOOD, good, null, "3 Nov 2026", "08.15 WIB")
        assertEquals("-3.2891, 114.0142", c.place)
        assertNull(c.coords)
    }

    @Test fun weakSignalShowsAccuracyInsteadOfPretendingToBeExact() {
        val weak = good.copy(accuracyM = 120f)
        val c = StampText.content(all, LocationState.WEAK, weak, "Maliku", "d", "t")
        assertEquals("-3.2891, 114.0142 ±120 m", c.coords)
    }

    @Test fun oldFixIsMarkedOldOnTheStamp() {
        val c = StampText.content(all, LocationState.STALE, good.copy(ageMs = 300_000L), "Maliku", "d", "t")
        assertEquals("lokasi lama (5 mnt)", c.note)
    }

    @Test fun noFixSaysSoAndKeepsDateAndTime() {
        val c = StampText.content(all, LocationState.SEARCHING, null, null, "3 Nov 2026", "08.15 WIB")
        assertEquals("Lokasi belum didapat", c.place)
        assertNull(c.coords)
        assertEquals("3 Nov 2026", c.date)
        assertEquals("08.15 WIB", c.time)
        // Only date and time wanted: no "location missing" line.
        val dt = StampText.content(StampFields(false, false, true, true), LocationState.SEARCHING, null, null, "d", "t")
        assertNull(dt.place)
        assertEquals("d · t", dt.dateTime)
    }
}
