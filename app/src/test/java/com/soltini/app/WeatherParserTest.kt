package com.soltini.app

import com.soltini.app.weather.WeatherParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeatherParserTest {
    // Minimal wttr.in j1 shape; values are test fixtures, not live data.
    private val sample = """
    {"current_condition":[{"temp_C":"21","FeelsLikeC":"20","humidity":"55","windspeedKmph":"9","uvIndex":"3",
       "weatherDesc":[{"value":"Partly cloudy"}]}],
     "nearest_area":[{"areaName":[{"value":"Testville"}],"region":[{"value":"Testshire"}]}],
     "weather":[
       {"date":"2026-10-17","maxtempC":"25","mintempC":"15","hourly":[
         {"time":"0","tempC":"16","weatherDesc":[{"value":"Clear"}]},
         {"time":"900","tempC":"20","weatherDesc":[{"value":"Sunny"}]},
         {"time":"1200","tempC":"24","weatherDesc":[{"value":"Sunny"}]},
         {"time":"1500","tempC":"25","weatherDesc":[{"value":"Cloudy"}]},
         {"time":"1800","tempC":"22","weatherDesc":[{"value":"Light rain"}]},
         {"time":"2100","tempC":"19","weatherDesc":[{"value":"Clear"}]}]},
       {"date":"2026-10-18","maxtempC":"26","mintempC":"16","hourly":[
         {"time":"0","tempC":"17","weatherDesc":[{"value":"Clear"}]}]}
     ]}
    """.trimIndent()

    @Test fun `parses current conditions`() {
        val w = WeatherParser.parse(JSONObject(sample), nowHour = 13)
        assertNotNull(w)
        w!!
        assertEquals("Testville, Testshire", w.place)
        assertEquals(21, w.tempC); assertEquals(20, w.feelsC)
        assertEquals(25, w.highC); assertEquals(15, w.lowC)
        assertEquals(55, w.humidity); assertEquals("Partly cloudy", w.desc)
    }

    @Test fun `hourly starts at the slot containing now`() {
        val w = WeatherParser.parse(JSONObject(sample), nowHour = 13)!!
        assertEquals("Now", w.hourly.first().label)
        assertEquals(24, w.hourly.first().tempC) // the 12:00 slot covers 13:00
        assertEquals(5, w.hourly.size)
    }

    @Test fun `missing essentials returns null instead of fake data`() {
        assertNull(WeatherParser.parse(JSONObject("{}"), nowHour = 10))
        assertNull(WeatherParser.parse(JSONObject("""{"current_condition":[{"humidity":"50"}],"weather":[]}"""), nowHour = 10))
    }
}
