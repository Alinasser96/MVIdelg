package com.example.mvi.core.plugins

/**
 * Plugin marker interfaces — implement these to get plugin capabilities.
 *
 * These are the opt-in switches. A ViewModel that declares `HasLoadingPlugin` gets a
 * `LoadingPluginImpl` installed automatically by the base class and a `loading` accessor
 * in scope; a ViewModel that does not declare it gets neither, and cannot even name the
 * accessor. Capability is decided by the type, and the compiler enforces it.
 */
interface HasLoadingPlugin

interface HasErrorPlugin

interface HasNavigationPlugin

interface HasLoggingPlugin
