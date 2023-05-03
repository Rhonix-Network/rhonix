package io.rhonix

import io.rhonix.models.ByteStringSyntax
import io.rhonix.models.ByteArraySyntax
import io.rhonix.models.StringSyntax

package object models {
  // Importing syntax object means using all extensions in the project
  object syntax extends AllSyntaxModels
}

// Models syntax
trait AllSyntaxModels extends ByteStringSyntax with ByteArraySyntax with StringSyntax
