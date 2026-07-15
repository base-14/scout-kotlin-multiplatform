package io.base14.scout.kmp

import io.base14.scout.core.ScoutConfig
import io.base14.scout.core.semantics.ScoutResourceAttributes

internal fun ScoutConfig.withKmpVersion(): ScoutConfig =
    copy(
        resourceAttributes =
            resourceAttributes + (ScoutResourceAttributes.SCOUT_KMP_VERSION to ScoutKmpBuildInfo.KMP_VERSION),
    )
