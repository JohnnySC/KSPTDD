package com.github.johnnysc.ksp.processor

import com.github.johnnysc.ksp.annotation.TDDDependency
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import kotlin.reflect.KClass

/**
 * @author Asatryan on 03.05.2023
 */
internal class TDDProcessor(
    private val fileGenerator: FileWriter
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val listedClasses: Sequence<KSClassDeclaration> =
            resolver.findAnnotations(TDDDependency::class)

        if (!listedClasses.iterator().hasNext()) return emptyList()

        fileGenerator.createFile(listedClasses)
        return listedClasses.filterNot { it.validate() }.toList()
    }

    private fun Resolver.findAnnotations(
        kClass: KClass<*>,
    ) = getSymbolsWithAnnotation(
        kClass.qualifiedName.toString()
    ).filterIsInstance<KSClassDeclaration>()
}
