package com.github.johnnysc.testksp.presentation

import com.github.johnnysc.ksp.annotation.TDDDependency
import com.github.johnnysc.testksp.InitialViewModel
import com.github.johnnysc.testksp.presentation.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@TDDDependency
class InitialViewModelTest {

    private lateinit var interactor: FakeInitialInteractor
    private lateinit var communication: FakeInitialCommunication
    private lateinit var navigation: FakeNavigation
    private lateinit var viewModel: InitialViewModel

    @Before
    fun setup() {
        interactor = FakeInitialInteractor.Base(functionsCallsStack)
        communication = FakeInitialCommunication.Base(functionsCallsStack)
        navigation = FakeNavigation.Base(functionsCallsStack)
        viewModel = InitialViewModel(
            interactor = interactor,
            communication = communication,
            navigation = navigation
        )
    }

    @Test
    fun `first opening success`() = runBlocking {
        interactor.changeExpected(InitialResult.FirstOpening)
        viewModel.init()
        communication.same(InitialUiState.Loading)
        interactor.same(InitialResult.FirstOpening)
        navigation.same(ChooseLanguageScreen)
        functionsCallsStack.checkStack(3)
    }

    @Test
    fun `not first opening success`() = runBlocking {
        interactor.changeExpected(InitialResult.NotFirstOpening)
        viewModel.init()
        communication.same(InitialUiState.Loading)
        interactor.same(InitialResult.NotFirstOpening)
        navigation.same(MainScreen)
        functionsCallsStack.checkStack(3)
    }

    @Test
    fun `first opening failure then retry and success`() = runBlocking {
        interactor.changeExpected(InitialResult.Error(message = "no connection"))
        viewModel.init()
        communication.same(InitialUiState.Loading)
        interactor.same(InitialResult.Error(message = "no connection"))
        communication.same(InitialUiState.Error(message = "no connection"))

        interactor.changeExpected(InitialResult.FirstOpening)
        viewModel.retry()
        communication.same(InitialUiState.Loading)
        interactor.same(InitialResult.FirstOpening)
        navigation.same(ChooseLanguageScreen)
        functionsCallsStack.checkStack(6)
    }

    private interface FakeInitialInteractor : InitialInteractor {

        fun same(other: InitialResult)

        fun changeExpected(expected: InitialResult)

        class Base(private val functionCallsStack: FunctionsCallsStack) : FakeInitialInteractor {
            private lateinit var result: InitialResult

            override fun same(other: InitialResult) {
                assertEquals(result, other)
                functionCallsStack.checkCalled(INTERACTOR_CALLED)
            }

            override fun changeExpected(expected: InitialResult) {
                result = expected
            }

            override suspend fun init(): InitialResult {
                functionCallsStack.put(INTERACTOR_CALLED)
                return result
            }
        }

        companion object {
            private const val INTERACTOR_CALLED = "interactor#init"
        }
    }

    private interface FakeInitialCommunication : InitialCommunication {

        fun same(other: InitialUiState)

        class Base(private val functionCallsStack: FunctionsCallsStack) : FakeInitialCommunication {
            private val list = mutableListOf<InitialUiState>()
            private var index = 0

            override fun map(source: InitialUiState) {
                functionCallsStack.put(COMMUNICATION_CALLED)
                list.add(source)
            }

            override fun same(other: InitialUiState) {
                assertEquals(list[index++], other)
                functionCallsStack.checkCalled(COMMUNICATION_CALLED)
            }
        }

        companion object {
            private const val COMMUNICATION_CALLED = "communication#map"
        }
    }

    private interface FakeNavigation : NavigationCommunication {

        fun same(other: Screen)

        class Base(private val functionCallsStack: FunctionsCallsStack) : FakeNavigation {
            private lateinit var screen: Screen

            override fun same(other: Screen) {
                assertEquals(screen, other)
                functionCallsStack.checkCalled(NAVIGATION_CALLED)
            }

            override fun map(source: Screen) {
                functionCallsStack.put(NAVIGATION_CALLED)
                screen = source
            }

            companion object {
                private const val NAVIGATION_CALLED = "navigation#map"
            }
        }
    }
}
