## Seoul signalized crosswalk inventory

`seoul_signalized_crosswalks.csv` is derived from the Seoul Open Data Plaza file
`서울시 교차로 및 횡단보도 시설·위치정보_20260824.xlsx` (downloaded October 2026).
Only rows whose `보행등유무` value is `유` are included. The exported columns are
the crosswalk ID, intersection ID, and projected X/Y coordinates.

- Source: https://data.seoul.go.kr/dataList/OA-23081/F/1/datasetView.do
- Coordinate reference system: EPSG:5186 (Korea 2000 / Central Belt 2010)
- License: Korea Open Government License Type 1; attribution required.
- Attribution: Seoul Metropolitan Government, “Seoul intersections and crosswalk facilities and locations,” updated 2026-09-07.

The source inventory is a snapshot. Refresh the CSV from the source file when the
dataset is updated, and retain this attribution with the data.
