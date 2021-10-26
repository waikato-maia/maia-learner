package māia.ml.learner.type

import māia.ml.dataset.headers.DataColumnHeaders

/** Type-alias for the signature of the checkHeaders function. */
typealias CheckHeadersFunction = (DataColumnHeaders, DataColumnHeaders) -> String?

/** Type-alias for the signature of the unionOf/intersectionOf functions. */
typealias CompositeTypeBuilderFunction = (Array<out LearnerType>) -> LearnerType
