import sys

with open('app/src/main/java/com/yansproject/app/ui/StartupScreen.kt', 'r') as f:
    content = f.read()

replacement = """
            try {
                val firestore = try { FirebaseFirestore.getInstance() } catch(e: Throwable) { null }
                if (firestore == null) {
                    _state.value = BootstrapState.FINISHED
                    return@launch
                }
                EnterpriseBootstrapEngine.executeFullBootstrap(
                    context = context,
                    db = db,
                    firestore = firestore,
"""

content = content.replace("""            try {
                EnterpriseBootstrapEngine.executeFullBootstrap(
                    context = context,
                    db = db,
                    firestore = firestore,""", replacement.strip())

with open('app/src/main/java/com/yansproject/app/ui/StartupScreen.kt', 'w') as f:
    f.write(content)
