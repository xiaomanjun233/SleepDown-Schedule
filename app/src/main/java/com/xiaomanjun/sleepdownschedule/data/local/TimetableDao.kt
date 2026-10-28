package com.xiaomanjun.sleepdownschedule.data.local

import androidx.room.*
import com.xiaomanjun.sleepdownschedule.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {
    @Query("SELECT * FROM period_schemes ORDER BY id") fun observeSchemes(): Flow<List<PeriodSchemeEntity>>
    @Query("SELECT * FROM period_scheme_times ORDER BY schemeId,periodIndex") fun observeTimes(): Flow<List<PeriodSchemeTimeEntity>>
    @Query("SELECT * FROM timetable_combos ORDER BY id") fun observeCombos(): Flow<List<TimetableComboEntity>>
    @Query("SELECT * FROM timetable_date_rules ORDER BY comboId,position") fun observeRules(): Flow<List<TimetableDateRuleEntity>>
    @Query("SELECT * FROM timetable_combos ORDER BY id") suspend fun getCombos(): List<TimetableComboEntity>
    @Query("SELECT * FROM timetable_date_rules ORDER BY comboId,position") suspend fun getRules(): List<TimetableDateRuleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCombo(value: TimetableComboEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRules(values: List<TimetableDateRuleEntity>)
    @Query("DELETE FROM timetable_combos") suspend fun deleteAllCombos()
    @Query("DELETE FROM timetable_date_rules WHERE comboId=:id") suspend fun deleteRules(id: Long)
    @Query("DELETE FROM timetable_combos WHERE id=:id") suspend fun deleteCombo(id: Long)
}
