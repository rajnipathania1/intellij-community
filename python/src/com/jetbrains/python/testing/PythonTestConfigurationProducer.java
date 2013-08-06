package com.jetbrains.python.testing;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.facet.PythonFacetSettings;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * User: ktisha
 */
abstract public class PythonTestConfigurationProducer extends RunConfigurationProducer<AbstractPythonTestRunConfiguration> {

  public PythonTestConfigurationProducer(final ConfigurationFactory configurationFactory) {
    super(configurationFactory);
  }

  @Override
  public boolean isConfigurationFromContext(AbstractPythonTestRunConfiguration configuration, ConfigurationContext context) {
    final Location location = context.getLocation();
    if (location == null || !isAvailable(location)) return false;
    final PsiElement element = location.getPsiElement();
    final PsiFile file = element.getContainingFile();
    final VirtualFile virtualFile = element instanceof PsiDirectory ? ((PsiDirectory)element).getVirtualFile() : file.getVirtualFile();
    final PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
    final PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

    final AbstractPythonTestRunConfiguration.TestType confType = configuration.getTestType();
    final String workingDirectory = configuration.getWorkingDirectory();

    if (element instanceof PsiDirectory) {
      final String path = ((PsiDirectory)element).getVirtualFile().getPath();
      return confType == AbstractPythonTestRunConfiguration.TestType.TEST_FOLDER &&
             path.equals(configuration.getFolderName()) ||
             path.equals(new File(workingDirectory, configuration.getFolderName()).getAbsolutePath());
    }

    final String scriptName = configuration.getScriptName();
    final boolean isTestFileEquals = virtualFile != null && scriptName.equals(virtualFile.getPath()) ||
                      scriptName.equals(new File(workingDirectory, scriptName).getAbsolutePath());

    if (pyFunction != null) {
      final String methodName = configuration.getMethodName();
      if (pyFunction.getContainingClass() == null) {
        return confType == AbstractPythonTestRunConfiguration.TestType.TEST_FUNCTION &&
               methodName.equals(pyFunction.getName()) && isTestFileEquals;
      }
      else if (pyFunction.getContainingClass() != null) {
        final String className = configuration.getClassName();

        return confType == AbstractPythonTestRunConfiguration.TestType.TEST_METHOD &&
               methodName.equals(pyFunction.getName()) &&
               pyClass != null && className.equals(pyClass.getName()) && isTestFileEquals;
      }
    }
    if (pyClass != null) {
      final String className = configuration.getClassName();
      return confType == AbstractPythonTestRunConfiguration.TestType.TEST_CLASS &&
             className.equals(pyClass.getName()) && isTestFileEquals;
    }
    return confType == AbstractPythonTestRunConfiguration.TestType.TEST_SCRIPT && isTestFileEquals;
  }

  @Override
  protected boolean setupConfigurationFromContext(AbstractPythonTestRunConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    if (context == null) return false;
    final Location location = context.getLocation();
    if (location == null || !isAvailable(location)) return false;
    final PsiElement element = location.getPsiElement();

    if (PythonUnitTestRunnableScriptFilter.isIfNameMain(location)) return false;
    final Module module = location.getModule();
    if (!isPythonModule(module)) return false;

    if (element instanceof PsiDirectory) {
      return setupConfigurationFromFolder((PsiDirectory)element, configuration);
    }

    final PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
    if (pyFunction != null && isTestFunction(pyFunction, configuration)) {
      return setupConfigurationFromFunction(pyFunction, configuration);
    }
    final PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    if (pyClass != null && isTestClass(pyClass, configuration)) {
      return setupConfigurationFromClass(pyClass, configuration);
    }

    final PsiFile file = element.getContainingFile();
    if (file instanceof PyFile && isTestFile((PyFile)file)) {
      return setupConfigurationFromFile((PyFile)file, configuration);
    }

    return false;
  }

  private static boolean setupConfigurationFromFolder(@NotNull final PsiDirectory element,
                                                      @NotNull final AbstractPythonTestRunConfiguration configuration) {
    final String path = element.getVirtualFile().getPath();

    configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_FOLDER);
    configuration.setFolderName(path);
    configuration.setWorkingDirectory(path);
    configuration.setName(configuration.suggestedName());
    return true;
  }

  protected boolean setupConfigurationFromFunction(@NotNull final PyFunction pyFunction,
                                                   @NotNull final AbstractPythonTestRunConfiguration configuration) {
    final PyClass containingClass = pyFunction.getContainingClass();
    configuration.setMethodName(pyFunction.getName());

    if (containingClass != null) {
      configuration.setClassName(containingClass.getName());
      configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_METHOD);
    }
    else {
      configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_FUNCTION);
    }
    return setupConfigurationScript(configuration, pyFunction);
  }

  protected boolean setupConfigurationFromClass(@NotNull final PyClass pyClass,
                                                @NotNull final AbstractPythonTestRunConfiguration configuration) {
    configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_CLASS);
    configuration.setClassName(pyClass.getName());
    return setupConfigurationScript(configuration, pyClass);
  }

  protected boolean setupConfigurationFromFile(@NotNull final PyFile pyFile,
                                               @NotNull final AbstractPythonTestRunConfiguration configuration) {
    configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_SCRIPT);
    return setupConfigurationScript(configuration, pyFile);
  }

  protected static boolean setupConfigurationScript(@NotNull final AbstractPythonTestRunConfiguration cfg,
                                                    @NotNull final PyElement element) {
    final PyFile containingFile = PyUtil.getContainingPyFile(element);
    if (containingFile == null) return false;
    final VirtualFile vFile = containingFile.getVirtualFile();
    if (vFile == null) return false;
    final VirtualFile parent = vFile.getParent();
    if (parent == null) return false;

    cfg.setScriptName(vFile.getPath());

    if (StringUtil.isEmptyOrSpaces(cfg.getWorkingDirectory()))
      cfg.setWorkingDirectory(parent.getPath());
    cfg.setName(cfg.suggestedName());
    return true;
  }

  protected boolean isAvailable(@NotNull final Location location) {
    return false;
  }

  protected boolean isTestClass(@NotNull final PyClass pyClass,
                                @Nullable final AbstractPythonTestRunConfiguration configuration) {
    return PythonUnitTestUtil.isTestCaseClass(pyClass);
  }

  protected boolean isTestFunction(@NotNull final PyFunction pyFunction,
                                   @Nullable final AbstractPythonTestRunConfiguration configuration) {
    return PythonUnitTestUtil.isTestCaseFunction(pyFunction);
  }

  protected boolean isTestFile(@NotNull final PyFile file) {
    final List<PyStatement> testCases = getTestCaseClassesFromFile(file);
    if (testCases.isEmpty()) return false;
    return true;
  }

  protected static boolean isPythonModule(Module module) {
    if (module == null) {
      return false;
    }
    if (ModuleType.get(module) instanceof PythonModuleTypeBase) {
      return true;
    }
    final Facet[] allFacets = FacetManager.getInstance(module).getAllFacets();
    for (Facet facet : allFacets) {
      if (facet.getConfiguration() instanceof PythonFacetSettings) {
        return true;
      }
    }
    return false;
  }

  protected List<PyStatement> getTestCaseClassesFromFile(@NotNull final PyFile pyFile) {
    return PythonUnitTestUtil.getTestCaseClassesFromFile(pyFile);
  }

}