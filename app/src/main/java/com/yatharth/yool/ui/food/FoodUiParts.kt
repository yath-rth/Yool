package com.yatharth.yool.ui.food

import com.yatharth.yool.domain.Nutrients
import com.yatharth.yool.ui.Fmt

internal fun macroLine(n: Nutrients): String =
    "${Fmt.int(Math.round(n.kcal))} kcal · P ${Fmt.one(n.proteinG)} · C ${Fmt.one(n.carbsG)} · F ${Fmt.one(n.fatG)}"
