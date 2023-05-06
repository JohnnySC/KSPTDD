package com.github.johnnysc.ksp.processor.provider

import com.github.johnnysc.ksp.processor.FileContentGenerator
import com.github.johnnysc.ksp.processor.FileWriter
import com.github.johnnysc.ksp.processor.TDDProcessor
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * @author Asatryan on 03.05.2023
 */
class TDDProcessProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val fileWriter = FileWriter(FileContentGenerator(), environment.codeGenerator)
        return TDDProcessor(fileWriter)
    }
}