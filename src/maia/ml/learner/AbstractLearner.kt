package maia.ml.learner

/*
 * TODO
 */

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maia.ml.dataset.AsyncDataStream
import maia.ml.dataset.DataBatch
import maia.ml.dataset.DataRow
import maia.ml.dataset.WithColumns
import maia.ml.dataset.headers.DataColumnHeaders
import maia.ml.dataset.util.copy
import maia.ml.learner.type.AnyLearnerType
import maia.ml.learner.type.LearnerType
import maia.util.*
import maia.util.property.classlevel.override
import maia.ml.learner.type.classLearnerType
import maia.ml.learner.type.learnerType
import kotlin.reflect.KClass

/**
 * Abstract base class for learners which implements some of the common
 * functionality.
 *
 * @param uninitialisedType
 *          The type of learner this is before it is initialised, or null
 *          to use the learner-type defined for the class.
 * @param datasetClass
 *          The type of data-set this learner can be trained on.
 */
abstract class AbstractLearner<in D : AsyncDataStream<*>>(
        uninitialisedType : LearnerType? = null,
        datasetClass : KClass<D>
) : Learner<D> {

    companion object {
        init {
            AbstractLearner<*>::classLearnerType.override(AnyLearnerType)
        }
    }

    override val uninitialisedType : LearnerType = uninitialisedType ?: this::class.learnerType

    override val isIncremental : Boolean = datasetClass isNotSubClassOf DataBatch::class

    override val isInitialised : Boolean
        get() = this::initialisedTypePrivate.isInitialized

    override val trainHeaders : DataColumnHeaders
        get() = ensureInitialised { trainHeadersPrivate }

    override val predictInputHeaders : DataColumnHeaders
        get() = ensureInitialised { predictInputHeadersPrivate }

    override val predictOutputHeaders : DataColumnHeaders
        get() = ensureInitialised { predictOutputHeadersPrivate }

    override val initialisedType : LearnerType
        get() = ensureInitialised { initialisedTypePrivate }

    /** Private view of the training headers. */
    private lateinit var trainHeadersPrivate : DataColumnHeaders

    /** Private view of the prediction input headers. */
    private lateinit var predictInputHeadersPrivate : DataColumnHeaders

    /** Private view of the prediction output headers. */
    private lateinit var predictOutputHeadersPrivate : DataColumnHeaders

    /** Private view of the initialised type. */
    private lateinit var initialisedTypePrivate : LearnerType

    /** Mutex protecting concurrent mutations of the learner. */
    private val mutex: Mutex = Mutex()

    protected fun <R> synchronisedSync(block: () -> R): R {
        if (!mutex.tryLock()) throw ConcurrentModificationException()

        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }

    protected suspend fun <R> synchronisedAsync(block: suspend () -> R): R {
        return mutex.withLock { block() }
    }

    // region Initialisation

    override fun initialise(headers : WithColumns) = synchronisedSync {
        // Capture a copy of the training headers
        val headersCopy = headers.headers.copy().readOnlyView

        // Perform the initialisation
        val (inputHeaders, outputHeaders, type) = performInitialisation(headersCopy)

        // Set the private views of the initialisation fields
        trainHeadersPrivate = headersCopy
        predictInputHeadersPrivate = inputHeaders.copy().readOnlyView
        predictOutputHeadersPrivate = outputHeaders.copy().readOnlyView
        initialisedTypePrivate = type
    }

    /**
     * Initialises the learner for working with data-sets matching the supplied
     * headers.
     *
     * @param headers
     *          The header signature of the data that will be used to train
     *          this learner.
     * @return
     *          - The headers that this learner requires to make predictions.
     *          - The headers of the predictions that this learner will make.
     *          - The type of this initialised learner.
     */
    protected abstract fun performInitialisation(
            headers : DataColumnHeaders
    ) : Triple<DataColumnHeaders, DataColumnHeaders, LearnerType>

    // endregion

    // region Training

    override suspend fun train(trainingDataset : D) = synchronisedAsync {
        ensureInitialised {
            // Perform the actual training
            performTrain(trainingDataset)
        }
    }

    /**
     * Trains the learner on a particular data-set.
     *
     * @param trainingDataset
     *          The data-set to train the learner on. Should match the headers
     *          that were passed to [initialise].
     */
    protected abstract suspend fun performTrain(trainingDataset : D)

    // endregion

    // region Prediction

    override fun predict(row : DataRow) : DataRow = synchronisedSync {
        ensureInitialised {
            performPredict(row)
        }
    }

    /**
     * Performs prediction for a data-row.
     *
     * @param row
     *          The prediction input data to use.
     * @return
     *          The predictions.
     */
    protected abstract fun performPredict(row : DataRow) : DataRow

    // endregion

}
