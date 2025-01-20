package app.palone.di

import app.palone.DishPredictor
import org.koin.dsl.module

fun predictorModule() = module {
    single { DishPredictor() }
}