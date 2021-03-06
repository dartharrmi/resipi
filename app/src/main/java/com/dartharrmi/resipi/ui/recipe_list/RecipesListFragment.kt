package com.dartharrmi.resipi.ui.recipe_list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.Toast
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.navigation.fragment.findNavController
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.dartharrmi.resipi.R
import com.dartharrmi.resipi.base.ResipiFragment
import com.dartharrmi.resipi.base.adapter.OnRecyclerViewItemClickListener
import com.dartharrmi.resipi.databinding.FragmentRecipeListBinding
import com.dartharrmi.resipi.domain.Recipe
import com.dartharrmi.resipi.ui.recipe_list.adapter.LoadingFooterAdapter
import com.dartharrmi.resipi.ui.recipe_list.adapter.RecipesAdapter
import com.dartharrmi.resipi.utils.gone
import com.dartharrmi.resipi.utils.hideKeyBoard
import com.dartharrmi.resipi.utils.visible
import kotlinx.android.synthetic.main.fragment_recipe_list.*
import kotlinx.android.synthetic.main.fragment_recipe_list.view.*
import org.koin.androidx.scope.currentScope
import org.koin.androidx.viewmodel.ext.android.viewModel
import retrofit2.HttpException
import java.io.IOException

class RecipesListFragment: ResipiFragment<FragmentRecipeListBinding>() {

    private companion object {
        const val ARG_QUERY = "KEY_QUERY"
    }

    private var isFirstLoading = true
    private var query = ""
    private val viewModel by currentScope.viewModel(this, RecipesListViewModel::class)
    private lateinit var recipesAdapter: RecipesAdapter

    override fun getLayoutId() = R.layout.fragment_recipe_list

    override fun getVariablesToBind(): Map<Int, Any> = emptyMap()

    override fun initObservers() = Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        requireActivity().setTheme(R.style.AppTheme)

        super.onCreate(savedInstanceState)
    }

    @ExperimentalPagingApi
    override fun initView(inflater: LayoutInflater, container: ViewGroup?) {
        super.initView(inflater, container)

        recipesAdapter = RecipesAdapter(requireContext(), object: OnRecyclerViewItemClickListener {
            override fun onItemClicked(item: Any?) {
                item?.let {
                    findNavController().navigate(RecipesListFragmentDirections.actionDestRecipeListToDestRecipeDetails(it as Recipe))

                }
            }
        }).apply {
            addLoadStateListener { loadState ->
                if (loadState.source.refresh is LoadState.Loading) {
                    dataBinding.root.rvRecipesList.gone()
                    dataBinding.root.animLoading.visible()
                    dataBinding.root.animLoading.playAnimation()

                    isFirstLoading = false
                } else {
                    dataBinding.root.rvRecipesList.visible()
                    dataBinding.root.animLoading.gone()
                    dataBinding.root.animLoading.cancelAnimation()
                }

                val errorState = loadState.source.append as? LoadState.Error
                        ?: loadState.source.prepend as? LoadState.Error
                        ?: loadState.append as? LoadState.Error
                        ?: loadState.prepend as? LoadState.Error
                        ?: loadState.refresh as? LoadState.Error
                errorState?.let {
                    val text = if (it.error is IOException) {
                        getString(R.string.internet_error)
                    } else {
                        it.error
                    }

                    Toast.makeText(
                            requireContext(),
                            "\uD83D\uDE28 Wooops! ${text}",
                            Toast.LENGTH_LONG
                    ).show()
                }
            }
            withLoadStateFooter(footer = LoadingFooterAdapter { retry() })
        }
        with(dataBinding.root) {
            rvRecipesList.apply {
                layoutManager = LinearLayoutManager(getViewContext())
                adapter = recipesAdapter
                viewTreeObserver
                        .addOnGlobalLayoutListener(object: OnGlobalLayoutListener {
                            override fun onGlobalLayout() {
                                rvRecipesList.viewTreeObserver.removeOnGlobalLayoutListener(this)

                                val appBarHeight: Int = this@with.appBarLayout.height
                                rvRecipesList.translationY = -appBarHeight.toFloat()
                                rvRecipesList.layoutParams.height =
                                        rvRecipesList.height + appBarHeight
                            }
                        })
            }

            svSearchRecipe.queryHint = getString(R.string.search_view_hint)
            svSearchRecipe.setOnQueryTextListener(object: OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    this@RecipesListFragment.query = query.orEmpty()
                    emptyState.gone()
                    search(query.orEmpty())
                    requireActivity().hideKeyBoard()

                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean = false
            })
        }
    }

    override fun onResume() {
        super.onResume()

        if (recipesAdapter.itemCount > 0) {
            emptyState.gone()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ARG_QUERY, query)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        savedInstanceState?.let {
            if (it.containsKey(ARG_QUERY)) {
                query = it.getString(ARG_QUERY).orEmpty()
                search(query)
            } else {
                search(query)
            }
        }
    }

    private fun search(query: String) {
        viewModel.getRecipes(query).doOnError {
            Toast.makeText(
                    requireContext(),
                    "\uD83D\uDE28 Wooops",
                    Toast.LENGTH_LONG
            ).show()
        }.subscribe { t ->
            recipesAdapter.submitData(lifecycle, t)
        }
    }
}