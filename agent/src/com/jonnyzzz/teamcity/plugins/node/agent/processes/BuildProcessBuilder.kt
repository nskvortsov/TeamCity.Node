/*
 * Copyright 2013-2013 Eugene Petrenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jonnyzzz.teamcity.plugins.node.agent.processes

import jetbrains.buildServer.agent.BuildProcess
import jetbrains.buildServer.agent.BuildFinishedStatus
import jetbrains.buildServer.agent.AgentRunningBuild
import com.jonnyzzz.teamcity.plugins.node.agent.block
import jetbrains.buildServer.agent.SimpleBuildLogger
import jetbrains.buildServer.agent.BuildProgressLogger

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 17.08.13 12:31
 */


public trait CompositeProcessBuilder<R> {
  fun execute(blockName : String, blockDescription : String = blockName, p: () -> Unit) : R
  fun delegate(blockName : String, blockDescription : String = blockName, p: () -> BuildProcess) : R
}

public fun compositeBuildProcess(build : AgentRunningBuild, builder: CompositeProcessBuilder<Unit>.() -> Unit): BuildProcess {
  val proc = CompositeBuildProcessImpl()
  object:CompositeProcessBuilderImpl<Unit>(build) {
    override fun push(p: BuildProcess) {
      proc.pushBuildProcess(p)
    }
  }.builder()
  return proc
}


abstract class CompositeProcessBuilderImpl<R>(val build : AgentRunningBuild) : CompositeProcessBuilder<R> {
  private val logger :BuildProgressLogger
    get() = build.getBuildLogger()

  override fun execute(blockName: String, blockDescription: String, p : () -> Unit) : R =
    delegate(blockName, blockDescription) {
      process(p)
    }

  override fun delegate(blockName: String, blockDescription: String, p : () -> BuildProcess) : R =
    push(logger.block(blockName, blockDescription, action(p)))


  fun push(p: DelegatingProcessAction) : R = push(DelegatingBuildProcess(p))
  protected abstract fun push(p: BuildProcess) : R

  private fun process(p : () -> Unit) : BuildProcess = object:BuildProcessBase() {
    protected override fun waitForImpl(): BuildFinishedStatus = with(p()) {BuildFinishedStatus.FINISHED_SUCCESS}
  }

  private fun action(p:() -> BuildProcess) : DelegatingProcessAction = object:DelegatingProcessAction {
    override fun startImpl(): BuildProcess = p()
  }

  private inline fun BuildProgressLogger.block(name: String,
                                               description: String = name,
                                               a: DelegatingProcessAction): DelegatingProcessAction {
    return object:DelegatingProcessAction {
      private var action: () -> Unit = { };
      override fun startImpl(): BuildProcess {
        action = block(name, description)
        a.startImpl()
      }
      override fun finishedImpl() {
        action()
        a.finishedImpl()
      }
    }
  }
}
