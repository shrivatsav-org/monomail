package com.shrivatsav.monomail.core.network.provider

/**
 * Thrown by EmailProvider implementations when a resource (thread, message)
 * returns HTTP 404 or 410, indicating it no longer exists on the server.
 * The repository should catch this and clean up stale local data.
 */
class ResourceNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause)
