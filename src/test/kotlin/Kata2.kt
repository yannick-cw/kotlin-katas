import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SealedClassesSpec : StringSpec({

    "should handle Loading state" {
        val state: NetworkState<String> = NetworkState.Loading
        state.toDisplayString() shouldBe "Loading.."
    }

    "should handle Success state with data" {
        val state: NetworkState<String> = NetworkState.Success("Hello")
        state.toDisplayString() shouldBe "Data: Hello"
    }

    "should handle Error state with message" {
        val state: NetworkState<String> = NetworkState.Error("Network timeout")
        state.toDisplayString() shouldBe "Error: Network timeout"
    }

    "should handle Empty state" {
        val state: NetworkState<String> = NetworkState.Empty
        state.toDisplayString() shouldBe "No data available"
    }

    "should map Success state" {
        val state: NetworkState<Int> = NetworkState.Success(5)
        val mapped = state.map { it * 2 }
        mapped shouldBe NetworkState.Success(10)
    }

//    "should not map Error state" {
//        val state: NetworkState<Int> = NetworkState.Error("fail")
//        val mapped = state.map { it * 2 }
//        mapped shouldBe Error("fail")
//    }
})


// TODO what does out mean here?
sealed class NetworkState<out T> {
    object Loading : NetworkState<Nothing>()
    object Empty : NetworkState<Nothing>()
    data class Success<T>(val data: T) : NetworkState<T>()
    data class Error<T>(val error: T) : NetworkState<T>()
}

fun <T> NetworkState<T>.toDisplayString(): String = when (this) {
    is NetworkState.Loading -> "Loading.."
    is NetworkState.Success -> "Data: $data"
    is NetworkState.Error -> "Error: $error"
    is NetworkState.Empty -> "No data available"
}


fun <T, R> NetworkState<T>.map(transform: (T) -> R): NetworkState<R> = when (this) {
    is NetworkState.Loading -> NetworkState.Loading
    is NetworkState.Success -> NetworkState.Success(transform(data))
    is NetworkState.Error -> NetworkState.Error(transform(error))
    is NetworkState.Empty -> NetworkState.Empty
}
