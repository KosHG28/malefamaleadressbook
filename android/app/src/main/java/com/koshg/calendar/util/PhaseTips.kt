package com.koshg.calendar.util

import java.time.LocalDate

/** Several short, general-purpose suggestions per phase for a partner reading the selected day's
 *  phase -- deliberately generic (not tailored to this couple's own logged data), since it's
 *  meant as a quick nudge, not a diagnosis. Several phrases per phase, rather than one fixed
 *  line, so repeatedly checking the tip (or the phase legend, see CalendarScreen's PhaseLegend)
 *  doesn't always show the exact same wording. */
private val phaseTipPool: Map<CyclePhase, List<String>> = mapOf(
    CyclePhase.MENSTRUAL to listOf(
        "Возможны спазмы и упадок сил. Уместнее забота без напора — грелка, чай, плед, фильм " +
            "рядом — и не давить на близость, если сама не проявит инициативу.",
        "Организм сейчас восстанавливается, энергии может быть меньше обычного. Взять на себя " +
            "бытовые мелочи — уже хорошая поддержка, без лишних слов.",
        "Дискомфорт и раздражительность в эти дни — это физиология, а не про тебя лично. Не " +
            "принимай резкость на свой счёт.",
        "Хороший момент предложить тёплую грелку или обезболивающее, если она забыла, а не " +
            "ждать, пока попросит сама."
    ),
    CyclePhase.FOLLICULAR to listOf(
        "Энергия и настроение обычно растут. Хорошее время для активных свиданий, новых идей " +
            "и совместных планов.",
        "Мотивация и работоспособность часто на подъёме — удобный момент обсудить общие планы " +
            "или начать что-то новое вдвоём.",
        "Настроение обычно ровное и открытое. Подходящее время для разговоров, которые давно " +
            "откладывали.",
        "Хорошее окно для активного отдыха — прогулка, спорт, поездка — энергии на это обычно " +
            "хватает с запасом."
    ),
    CyclePhase.OVULATORY to listOf(
        "Часто пик энергии и либидо. Подходящий момент для романтики и близости — но " +
            "ориентируйтесь на её настроение в моменте, а не только на фазу.",
        "Уверенность и общительность нередко на максимуме в эти дни — хорошее время для " +
            "чего-то яркого вдвоём.",
        "Пик цикла не гарантирует настроение — он лишь немного повышает шансы, финальное слово " +
            "всегда за тем, как она сама себя чувствует.",
        "Если давно не было свидания — эти дни статистически удачнее прочих, чтобы его " +
            "предложить."
    ),
    CyclePhase.LUTEAL to listOf(
        "Ближе к концу фазы возможны перепады настроения и ПМС. Больше терпения, меньше " +
            "критики — спокойный тихий вечер обычно заходит лучше активных планов.",
        "Тяга к сладкому, усталость, чувствительность к мелочам — обычные спутники этой фазы, " +
            "а не повод для конфликта.",
        "Хорошее время сбавить обороты вместе — меньше шумных планов, больше спокойного " +
            "времени рядом.",
        "Если она вдруг более резкая или закрытая, чем обычно — это чаще фаза цикла, чем " +
            "отношение к тебе."
    )
)

/** Deterministically picks one of [phaseTipPool]'s phrases for [phase] using [date] as the seed
 *  -- stable within a single day (so re-opening the tip or the legend mid-day doesn't shuffle
 *  the wording underfoot), but varies from day to day. */
fun phaseTipForMen(phase: CyclePhase, date: LocalDate = LocalDate.now()): String {
    val pool = phaseTipPool.getValue(phase)
    val seed = date.toEpochDay() + phase.ordinal
    val index = ((seed % pool.size) + pool.size) % pool.size
    return pool[index.toInt()]
}
