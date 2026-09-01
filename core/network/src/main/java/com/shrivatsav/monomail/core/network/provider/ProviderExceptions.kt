package com.shrivatsav.monomail.core.network.provider

/**
 * Thrown by EmailProvider implementations when a resource (thread, message)
 * returns HTTP 404 or 410, indicating it no longer exists on the server.
 * The repository should catch this and clean up stale local data.
 */
class ResourceNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A provider returned successfully, but did not return usable thread content. */
class IncompleteProviderResponseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** MIME content was present but could not be fetched or decoded. */
class ProviderContentException(message: String, cause: Throwable? = null) : Exception(message, cause)
