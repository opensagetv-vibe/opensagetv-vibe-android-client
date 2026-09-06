# Third-party release material

`RUNTIME_DEPENDENCIES.csv` is the machine-readable inventory of every selected
Maven coordinate in the three active runtime graphs. It is generated from the
captured Gradle report by `scripts/generate_runtime_dependency_inventory.py`.
The generator fails when a new dependency lacks an explicit reviewed license
mapping.

Full license texts that are not already supplied by the repository-root
Apache-2.0 `LICENSE` are in `licenses/`. Component-specific notices remain in
`THIRD_PARTY_NOTICES.md`. Native LGPL source and reconstruction information is
in `source-offers/README.md`.

These materials support release review; they are not legal advice. A changed
dependency graph or checked-in native AAR requires a new review.
