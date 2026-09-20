package com.nickbether.pebbletasker.tasker.base;

import android.content.Context;
import android.content.Intent;
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginConditionResult;
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerCondition;

/** Compile-time adapter for the pinned 0.4.10 SDK's Kotlin-internal JVM entry point.
 * Keeps its reflection, variable substitution and result ABI, without its parallel services.
 * An SDK upgrade must revalidate this adapter and the real SDK contract tests. */
public final class ConditionSdk {
    private ConditionSdk() {}
    public static TaskerPluginConditionResult evaluate(Context context, Intent intent) {
        return TaskerPluginRunnerCondition.Companion.getResultFromIntent$taskerpluginlibrary_release(context, intent);
    }
}
