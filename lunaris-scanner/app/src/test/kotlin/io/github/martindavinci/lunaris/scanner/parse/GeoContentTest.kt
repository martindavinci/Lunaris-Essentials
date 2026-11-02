package io.github.martindavinci.lunaris.scanner.parse

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Geo
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Text
import org.junit.Test

class GeoContentTest {
    @Test fun coordinates() {
        assertThat(parse("geo:45.4642,9.19")).isEqualTo(Geo(45.4642, 9.19, null))
        assertThat(parse("GEO:-33.8,-151")).isEqualTo(Geo(-33.8, -151.0, null))
        assertThat(parse("geo:+1.5,.5")).isEqualTo(Geo(1.5, 0.5, null))
        assertThat(parse("geo:1.,2")).isEqualTo(Geo(1.0, 2.0, null))
    }

    @Test fun altitudeAndUriParametersAreDropped() {
        assertThat(parse("geo:1,2,300")).isEqualTo(Geo(1.0, 2.0, null))
        assertThat(parse("geo:1,2;u=35")).isEqualTo(Geo(1.0, 2.0, null))
        assertThat(parse("geo:1,2,3;crs=wgs84;u=35")).isEqualTo(Geo(1.0, 2.0, null))
    }

    @Test fun rangeLimitsAreInclusive() {
        assertThat(parse("geo:90,180")).isEqualTo(Geo(90.0, 180.0, null))
        assertThat(parse("geo:-90,-180")).isEqualTo(Geo(-90.0, -180.0, null))
    }

    @Test fun query() {
        assertThat(parse("geo:0,0?q=Piazza%20Duomo")).isEqualTo(Geo(0.0, 0.0, "Piazza Duomo"))
        assertThat(parse("geo:0,0?q=Piazza+Duomo%2C+Milano"))
            .isEqualTo(Geo(0.0, 0.0, "Piazza Duomo, Milano"))
        assertThat(parse("geo:1,2?z=10&Q=x")).isEqualTo(Geo(1.0, 2.0, "x"))
        assertThat(parse("geo:1,2;u=35?q=x")).isEqualTo(Geo(1.0, 2.0, "x"))
        assertThat(parse("geo:1,2?z=10")).isEqualTo(Geo(1.0, 2.0, null))
        assertThat(parse("geo:1,2?q=")).isEqualTo(Geo(1.0, 2.0, null))
    }

    @Test fun outOfRangeIsText() {
        listOf("geo:90.1,0", "geo:-90.1,0", "geo:0,180.1", "geo:0,-180.1", "geo:999,999").forEach {
            assertThat(parse(it)).isEqualTo(Text(it))
        }
    }

    @Test fun malformedIsText() {
        val malformed = listOf(
            "geo:",
            "geo:1",
            "geo:1,",
            "geo:,2",
            "geo:a,b",
            "geo:1,2,x",
            "geo:1,2,3,4",
            "geo:1, 2",
            "geo:?q=x",
            "geo:NaN,0",
            "geo:0,Infinity",
            "geo:1e1,0",
            "geo:0x1p3,0",
            "geo:1f,0",
            "geo:.,0",
            "geo:1" + "0".repeat(400) + ",0",
        )
        malformed.forEach { assertThat(parse(it)).isEqualTo(Text(it)) }
    }

    @Test fun digitsOtherThanAsciiAreNotCoordinates() {
        listOf("geo:\u0661,\u0662", "geo:1.5,\u0662", "geo:\uFF11,\uFF12").forEach { text ->
            assertThat(parse(text)).isEqualTo(Text(text))
        }
    }
}
