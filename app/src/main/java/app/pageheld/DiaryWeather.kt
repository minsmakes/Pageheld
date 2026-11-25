package app.pageheld

internal data class DiaryWeather(val code: String, val korean: String, val icon: Int)

internal val weatherIllustrations =
    listOf(
        DiaryWeather("Sunny", "맑음", R.drawable.weather_sunny),
        DiaryWeather("Partly cloudy", "구름 조금", R.drawable.weather_partly_cloudy),
        DiaryWeather("Cloudy", "구름 많음", R.drawable.weather_cloudy),
        DiaryWeather("Overcast", "흐림", R.drawable.weather_overcast),
        DiaryWeather("Rainy", "가벼운 비", R.drawable.weather_light_rain),
        DiaryWeather("Heavy rain", "폭우", R.drawable.weather_heavy_rain),
        DiaryWeather("Stormy", "뇌우", R.drawable.weather_storm),
        DiaryWeather("Snowy", "눈", R.drawable.weather_snow),
        DiaryWeather("Foggy", "안개", R.drawable.weather_fog),
        DiaryWeather("Windy", "바람", R.drawable.weather_wind),
        DiaryWeather("Clear night", "맑은 밤", R.drawable.weather_clear_night),
        DiaryWeather("Cloudy night", "구름 낀 밤", R.drawable.weather_cloudy_night),
        DiaryWeather("Unknown", "알 수 없음", R.drawable.weather_unknown),
    )

internal fun weatherFor(code: String) =
    weatherIllustrations.firstOrNull { it.code == code } ?: weatherIllustrations.last()
