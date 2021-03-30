<!-- [![CI](https://github.com/day8/shadow-git-inject/workflows/ci/badge.svg)](https://github.com/day8/shadow-git-inject/actions?workflow=ci)
[![CD](https://github.com/day8/shadow-git-inject/workflows/cd/badge.svg)](https://github.com/day8/shadow-git-inject/actions?workflow=cd)
[![GitHub tag (latest by date)](https://img.shields.io/github/v/tag/day8/shadow-git-inject?style=flat)](https://github.com/day8/shadow-git-inject/tags) 
[![GitHub pull requests](https://img.shields.io/github/issues-pr/day8/shadow-git-inject)](https://github.com/day8/shadow-git-inject/pulls)-->
[![Clojars Project](https://img.shields.io/clojars/v/day8/shadow-git-inject?style=for-the-badge&logo=clojure&logoColor=fff)](https://clojars.org/day8/shadow-git-inject)
[![GitHub issues](https://img.shields.io/github/issues-raw/day8/shadow-git-inject?style=for-the-badge)](https://github.com/day8/shadow-git-inject/issues)
[![License](https://img.shields.io/github/license/day8/shadow-git-inject?style=for-the-badge)](LICENSE)

# shadow-git-inject

shadow-cljs build hook which computes the "version" at build-time - from the ambient git context (think latest tag).

Normally, shadow-cljs has no built in concept of versions.

But, when using this build hook, your build configuration will contain a placeholder keyword (or string), :shadow-git-inject/version like this: 
```clj
:compiler-options {:closure-defines {my-app.config/version :shadow-git-inject/version}}
```

Then, at build time, this middleware will:
   1. apply ***a two-rule method*** to compute the "version" from ***the ambient git context***. We refer to this as `the computed version`.
   2. replace the placeholder string with `the computed version`
 
It facilitates embedding `the computed version` (and certain other build-time values) 
within your ClojureScript application, making it readily available at run-time for purposes like logging.

## The Ambient Git Context

Imagine you are at the command line in a git repo, and you execute:
```sh
$ git describe --tags --dirty=-dirty
```
If the latest tag in your branch was `v1.0.4`, this command might output something like:
```sh
v1.0.4-3-g975b-dirty
```
which encodes four (hyphen separated) values which we refer to as "the ambient git context":
  - the latest git tag: "v1.0.4"
  - the number of commits the repo is currently "ahead" of that latest tag: "3" 
  - the short ref (SHA) for the commit referenced by that latest tag: "g975b"
  - an indication that there are uncommitted changes: "dirty"  (or absent)
  
## The Two-Rule Method

This build hook creates `the computed version` from these four "ambient" values by applying two rules:
  1. when the "ahead" count is 0, and the repo is not dirty, `the computed version` will just be the latest tag (eg: `1.0.4`)
  2. when the "ahead" count is non-zero, or the repo is dirty, `the computed version` will be the tag suffixed with `-<ahead-count>-<short-ref>-SNAPSHOT`, e.g. `1.0.4-3-g975b-SNAPSHOT`

***Note #1:*** there is a configuration option to ignore `dirty` state. See the Configuration section below.

***Note #2:*** only part of the latest tag is used (just `1.0.4`, not the full string `v1.0.4`) but that's explained in the next section. 

## The Latest Tag

So far, we have said `the computed version` is created using the "latest tag". While that is often true, it is not the whole story, which is acually as follows:
  1. what's used is the "latest version tag" found in the commit history  ("latest version tag" vs "latest tag")
  2. where a "version tag" is a tag with a specific textual structure
  3. by default, that textual structure must match the regex: `#"^v(\d+\.\d+\.\d+)$"`
  4. so, one of these "version tags" might look like: `v1.2.3`  (the string `"v"` followed by a semver, `"N.N.N"`)
  5. tags which do not match the regex are ignored (which means you can use tags for other purposes, not just for nominating versions)
  6. you can override this default regex with one of your own which will recognise an alternative textual structure (see how below)
  7. you'll notice that the regex has a capturing group which extracts just the semver part: "N.N.N". If you provide your own regex, it must contain a single capturing group which isolates that part of the tag to be used in `the computed version`.
  
So, this build hook will traverse backwards through the history of the current commit looking for a tag which has the right structure (matches the regex), and when it finds one, it is THAT tag which is used to create `the computed version` - it is that tag against which the "ahead count" will be calculated, etc.

## Sharp Edges
  
Please be aware of the following: 
  - if no matching tag is found then `the computed version` will be `git-version-tag-not-found`
  - this build hook obtains the "ambient git context" by shelling out to the `git` executable. If this executable is not in the PATH, then you'll see messages on `stderr` and `the computed version` will be `git-command-not-found`

## The Two Steps

The two-step narrative presented above says this build hook:
  1. creates `the computed version` 
  2. replaces a placeholder string within build configuration(s) with `the computed version`

While that's true, it is a simplification. The real steps are:
  1. this build hook computes **four** build-time values, of which `the computed version` is just one
  2. this build hook will perform a search and replace across ***all the `EDN`*** in 
the current build's configuration block, looking for four special string values and, where they are found, it will replace them with the associated computed value from step 1. 

So, the special string "shadow-git-inject/version" will be replaced ***anywhere*** it is found within the build configuration EDN.

When you consider this second step, keep in mind that this build hook runs 
very early in the shadow-cljs build pipeline at the `configure` step. 

The four special strings supported - referred to as `substitution keys` - are: 


|   substitution key                     |    example replacement       |
|----------------------------------------|------------------------------|
| :shadow-git-inject/version             | "12.4.1-2-453a730-SNAPSHOT"  |
| :shadow-git-inject/build-iso-date-time | "2019-11-18T00:05:02.273361" |      
| :shadow-git-inject/build-iso-date-week | "2019-W47-2"                 |
| :shadow-git-inject/user-name           | "Isaac"                      |

## Embedding Build-Time Values In Your App

This build hook provides a way
to embed any of these four build-time values into our ClojureScript application. 
This is often a very useful outcome - these values are useful at runtime for display and logging purposes. And it can 
be achieved in an automated, DRY way.

The trick is to place the substitution keys into specific places within the build configuration - ones which control 
the actions of the ClojureScript compiler. We want to take advantage of the [`:closure-defines` feature](https://clojurescript.org/reference/compiler-options#closure-defines) feature of the ClojureScript complier which permits us to "set" values for `defs` at compile time.

Below, the Annotated Example section demonstrates how to achieve this outcome using shadow-cljs.


## Configuration

A map of configuration options can, optionally, be added to the build configuration via the key `:git-inject`, like this:

```clj
{:builds {:my-app {:target :browser
                   
		   :git-inject { ... } ;; a map of configuration options
		   }}}
```

The two configuration options are:
  -  `:ignore-dirty?` 
  -  `:version-pattern` 
  
#### :ignore-dirty?

A boolean value which specifies if the dirty state of the repo should be ignored when calculating the version. 

Defaults to `false`.

Can be supplied as an explicit boolean or via an environment variable as the string "true" or "false".

```clj
:git-inject {
  :ignore-dirty? true
}
```
OR
```clj
:git-inject {
  ;; Will only be true if IGNORE_DIRTY environment variable is the string "true"
  ;; If the environment variable is not found, defaults to "false"
  :ignore-dirty? :env/ignore_dirty
}
```

#### :version-pattern

A regex which is used to differentiate between `version tags` and other `tags`. If this regex
matches, then the tag is assumed to be a `version tag`, otherwise the tag will be ignored. 

Defaults to `#"^v(\d+\.\d+\.\d+)$"`

When designing the textual structure for your "version tags", remember that 
git tags are git references and that there are rules about well formedness. 
For example, you can't have a ":" in a tag. See https://git-scm.com/docs/git-check-ref-format

The regex you supply has two jobs:
  1. to "match" version tags 
  2. to return one capturing group which extracts the text within the tag which is to 
     be used as the version itself. In the example below, the regex will match the tag "version/1.2.3" 
     but it will also capture the "1.2.3" part and it is THAT part which will be used in the computed version. 
    
```clj
:git-inject {
  :version-pattern  #"^version\/(.*)$" 
}
```
  
## An Annotated Example

Here's how to write your `shadow-cljs-edn` ... 

```clojure

;; On the next line, note that the version (2nd argument of defproject) is a 
;; `Substitution Key` which will be replaced by `the computed version` which is
;; built from `The Ambient Git Context`, using `The Two-Rule Method`.
{:dependencies [[day8/shadow-git-inject "0.0.1"]] ;; <--- you must include this dependency

 :builds {:app {:target :browser
                 
		:build-hooks [(shadow-git-inject.core/hook)] ;; <--- you must include this build hook

		  ;; Embedding
		  ;; If you are using the shadow-clj compiler and lein-shadow, the shadow-cljs 
		  ;; configuration is put here in project.clj. Below is an example of how to 
		  ;; combine this middleware with a `:clojure-define` in order to 
		  ;; inject build-time values into your application, for later run-time use.
		  ;; 
		  ;; You'll notice the use of the substitution key "shadow-git-inject/version".  
		  ;; At build time, this middleware will replace that keyword with `the computed version`.
		  ;; In turn, that value is used within a `:clojure-define` to bind it
		  ;; to a var, via a `def` in your code (called `version` within the namespace `some.namespace`). 
		:compiler-options {:closure-defines {some.namespace.version  "shadow-git-inject/version"}}

	        ;; Optional - see the `Configuration` section for explanation
	        :git-inject {
	          :version-pattern  #"^version\/(.*)$" 
	          :ignore-dirty? true}}}}
```


## License

Copyright © 2021 Mike Thompson

Derived from lein-git-inject © 2020 Mike Thompson

Derived from cuddlefish © 2018 Reid "arrdem" McKenzie

Derived from lein-git-version © 2017 Reid "arrdem" McKenzie

Derived from lein-git-version © 2016 Colin Steele

Derived from lein-git-version © 2011 Michał Marczyk

Distributed under the Eclipse Public License, the same as Clojure.


