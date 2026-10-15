plugins {
    alias(libs.plugins.nursemate.qnn.feature)
}

// 세대와 무관한 QNN 토대 — EP 번역기 + libQnnHtp · libQnnSystem (9.2MB).
// 세대별 Skel 은 qnn_v69 · qnn_v73 · qnn_v75 · qnn_v79 · qnn_v81 이 따로 들고 온다.
// 자세한 것은 QnnFeatureConventionPlugin KDoc.
