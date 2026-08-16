package com.juhao.murexide.ui.mine

import org.junit.Assert.assertEquals
import org.junit.Test

class ChinaRegionDataTest {
    @Test
    fun `empty region data resolves to the safe zero indices`() {
        assertEquals(
            RegionIndices(0, 0, 0),
            ChinaRegionData.resolve(
                regions = emptyList(),
                provinceName = "",
                cityName = "",
                districtName = "",
                locationCode = ""
            )
        )
    }

    @Test
    fun `a matching location code takes priority over stale names`() {
        val indices = ChinaRegionData.resolve(
            regions = regions,
            provinceName = "wrong province",
            cityName = "wrong city",
            districtName = "wrong district",
            locationCode = "310102"
        )

        assertEquals(RegionIndices(1, 0, 1), indices)
    }

    @Test
    fun `names resolve a complete location when code is absent`() {
        val indices = ChinaRegionData.resolve(
            regions = regions,
            provinceName = "上海",
            cityName = "上海城区",
            districtName = "静安区",
            locationCode = ""
        )

        assertEquals(RegionIndices(1, 0, 1), indices)
    }

    @Test
    fun `partial code selects province and city while unknown names stay in bounds`() {
        val byCode = ChinaRegionData.resolve(
            regions = regions,
            provinceName = "",
            cityName = "",
            districtName = "",
            locationCode = "3101"
        )
        val fallback = ChinaRegionData.resolve(
            regions = regions,
            provinceName = "not found",
            cityName = "not found",
            districtName = "not found",
            locationCode = ""
        )

        assertEquals(RegionIndices(1, 0, 0), byCode)
        assertEquals(RegionIndices(0, 0, 0), fallback)
    }

    private val regions = listOf(
        RegionProvince(
            code = "110000",
            name = "北京",
            cities = listOf(
                RegionCity(
                    code = "110100",
                    name = "北京城区",
                    districts = listOf(
                        RegionItem("110101", "东城区"),
                        RegionItem("110102", "西城区")
                    )
                )
            )
        ),
        RegionProvince(
            code = "310000",
            name = "上海",
            cities = listOf(
                RegionCity(
                    code = "310100",
                    name = "上海城区",
                    districts = listOf(
                        RegionItem("310101", "黄浦区"),
                        RegionItem("310102", "静安区")
                    )
                )
            )
        )
    )
}
