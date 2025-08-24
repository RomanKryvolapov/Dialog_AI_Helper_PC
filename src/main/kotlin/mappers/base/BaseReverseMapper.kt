/**
 * Created & Copyright 2025 by Roman Kryvolapov
 **/
package mappers.base

abstract class BaseReverseMapper<From, To> : BaseMapper<From, To>() {

    abstract fun reverse(model: To): From

    fun reverseList(models: List<To>): List<From> {
        return models.map(::reverse)
    }

}