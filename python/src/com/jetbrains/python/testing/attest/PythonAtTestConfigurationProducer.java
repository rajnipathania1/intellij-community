package com.jetbrains.python.testing.attest;

import com.google.common.collect.Lists;
import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.testing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
/**
 * User: catherine
 */
public class PythonAtTestConfigurationProducer extends
                                                 PythonTestConfigurationProducer {
  public PythonAtTestConfigurationProducer() {
    super(PythonTestConfigurationType.getInstance().PY_ATTEST_FACTORY);
  }

  protected boolean isAvailable(@NotNull final Location location) {
    final PsiElement element = location.getPsiElement();
    Module module = location.getModule();
    if (module == null) module = ModuleUtilCore.findModuleForPsiElement(element);

    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    return module != null && TestRunnerService.getInstance(module).getProjectConfiguration().equals(
      PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME) && sdk != null;
  }

  @Override
  protected boolean isTestClass(@NotNull final PyClass pyClass, @Nullable final AbstractPythonTestRunConfiguration configuration) {
    for (PyClassLikeType type : pyClass.getAncestorTypes(TypeEvalContext.codeInsightFallback())) {
      if (type != null && "TestBase".equals(type.getName()) && hasTestFunction(pyClass)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasTestFunction(@NotNull final PyClass pyClass) {
    PyFunction[] methods = pyClass.getMethods();
    for (PyFunction function : methods) {
      PyDecoratorList decorators = function.getDecoratorList();
      if (decorators == null) continue;
      for (PyDecorator decorator : decorators.getDecorators()) {
        if ("test".equals(decorator.getName()) || "test_if".equals(decorator.getName()))
          return true;
      }
    }
    return false;
  }

  protected boolean isTestFunction(@NotNull final PyFunction pyFunction,
                                   @Nullable final AbstractPythonTestRunConfiguration configuration) {
    PyDecoratorList decorators = pyFunction.getDecoratorList();
    if (decorators == null) return false;
    for (PyDecorator decorator : decorators.getDecorators()) {
      if ("test".equals(decorator.getName()) || "test_if".equals(decorator.getName()))
        return true;
    }
    return false;
  }

  protected List<PyStatement> getTestCaseClassesFromFile(@NotNull final PyFile file) {
    List<PyStatement> result = Lists.newArrayList();
    for (PyClass cls : file.getTopLevelClasses()) {
      if (isTestClass(cls, null)) {
        result.add(cls);
      }
    }

    for (PyFunction cls : file.getTopLevelFunctions()) {
      if (isTestFunction(cls, null)) {
        result.add(cls);
      }
    }
    return result;
  }
}