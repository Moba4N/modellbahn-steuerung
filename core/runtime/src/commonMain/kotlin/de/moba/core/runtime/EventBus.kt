package de.moba.core.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * Verteilt Ereignisse zwischen Modulen. Der Absender weiss nicht, wer zuhoert,
 * deshalb koennen neue Module mithoeren, ohne dass andere geaendert werden.
 */
class EventBus {
    private val flow = MutableSharedFlow<Any>(extraBufferCapacity = 1024)
    val events: SharedFlow<Any> = flow

    suspend fun publish(event: Any) = flow.emit(event)
}

/**
 * Abonniert alle Ereignisse vom Typ [T], solange [scope] lebt.
 * Das Abo ist sofort aktiv, wenn die Funktion zurueckkehrt; es geht kein Ereignis verloren.
 */
inline fun <reified T : Any> EventBus.on(scope: CoroutineScope, noinline handler: suspend (T) -> Unit): Job =
    scope.launch(start = CoroutineStart.UNDISPATCHED) { events.filterIsInstance<T>().collect { handler(it) } }
