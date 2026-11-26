package com.lagradost.cloudstream3.ui.categories

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.CategoryTileBinding
import com.lagradost.cloudstream3.databinding.FragmentCategoriesBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

class CategoriesFragment : BaseFragment<FragmentCategoriesBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentCategoriesBinding::inflate)
) {
    private val viewModel: CategoriesViewModel by viewModels()
    private var tileAdapter: TileAdapter? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.close()
        }
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padBottom = isLandscape(),
            padLeft = !isLayout(PHONE)
        )
    }

    override fun onBindingCreated(binding: FragmentCategoriesBinding, savedInstanceState: Bundle?) {
        val ctx = binding.root.context
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        val adapter = TileAdapter { category -> viewModel.open(ctx, category) }
        tileAdapter = adapter
        val spans = if (isLandscape()) 4 else 2
        binding.categoriesGrid.layoutManager = GridLayoutManager(ctx, spans)
        binding.categoriesGrid.adapter = adapter

        val results = SearchAdapter(binding.categoriesResults) { callback ->
            SearchHelper.handleSearchClickCallback(callback)
        }
        binding.categoriesResults.adapter = results

        binding.categoriesBack.setOnClickListener { viewModel.close() }

        buildTypeChips(binding.categoriesTypeChips)

        observe(viewModel.covers) { covers -> adapter.covers = covers }

        observeNullable(viewModel.result) { state ->
            val open = state != null
            backCallback.isEnabled = open
            binding.categoriesGridHolder.isVisible = !open
            binding.categoriesResultsHolder.isVisible = open
            when (state) {
                null -> results.submitList(emptyList())
                is Resource.Loading -> {
                    binding.categoriesResultsTitle.text = viewModel.selected?.let { getString(it.title) }
                    binding.categoriesLoading.isVisible = true
                    binding.categoriesEmpty.isVisible = false
                    results.submitList(emptyList())
                    binding.categoriesTypeChips.children.forEachIndexed { index, view ->
                        (view as? Chip)?.isChecked = index == 0
                    }
                    binding.categoriesLangChips.removeAllViews()
                }
                is Resource.Success -> {
                    val data = state.value
                    binding.categoriesResultsTitle.text = getString(data.category.title)
                    binding.categoriesLoading.isVisible = false
                    binding.categoriesEmpty.isVisible = data.items.isEmpty()
                    results.submitList(data.items)
                    buildLanguageChips(binding.categoriesLangChips, data.languages)
                }
                is Resource.Failure -> {
                    binding.categoriesLoading.isVisible = false
                    binding.categoriesEmpty.isVisible = true
                }
            }
        }

        viewModel.loadCovers(ctx)
    }

    private fun buildTypeChips(group: ChipGroup) {
        group.removeAllViews()
        val labels = mapOf(
            TypeFilter.All to R.string.filter_all,
            TypeFilter.Movies to R.string.filter_movies,
            TypeFilter.Series to R.string.filter_series,
            TypeFilter.Anime to R.string.filter_anime,
        )
        labels.forEach { (filter, label) ->
            val chip = newChip(group, getString(label))
            chip.isChecked = filter == viewModel.typeFilter
            chip.setOnClickListener {
                viewModel.typeFilter = filter
                viewModel.publish()
            }
            group.addView(chip)
        }
    }

    private fun buildLanguageChips(group: ChipGroup, languages: List<String>) {
        (group.parent as? View)?.isVisible = languages.size > 1
        if (group.childCount == languages.size + 1) return
        group.removeAllViews()
        val all = newChip(group, getString(R.string.filter_all))
        all.isChecked = viewModel.langFilter == null
        all.setOnClickListener {
            viewModel.langFilter = null
            viewModel.publish()
        }
        group.addView(all)
        languages.take(10).forEach { iso ->
            val label = SubtitleHelper.getNameNextToFlagEmoji(iso) ?: iso.uppercase()
            val chip = newChip(group, label)
            chip.isChecked = viewModel.langFilter == iso
            chip.setOnClickListener {
                viewModel.langFilter = iso
                viewModel.publish()
            }
            group.addView(chip)
        }
    }

    private fun newChip(group: ChipGroup, label: String): Chip =
        (layoutInflater.inflate(R.layout.category_chip, group, false) as Chip).apply {
            id = View.generateViewId()
            text = label
            isCheckable = true
        }

    override fun onDestroyView() {
        tileAdapter = null
        super.onDestroyView()
    }

    private class TileAdapter(
        private val onClick: (Category) -> Unit
    ) : RecyclerView.Adapter<TileAdapter.Holder>() {
        var covers: Map<String, String> = emptyMap()
            set(value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }

        class Holder(val binding: CategoryTileBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(CategoryTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = CategoryCatalog.all.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val category = CategoryCatalog.all[position]
            val b = holder.binding
            b.categoryTitle.setText(category.title)
            b.categoryEmoji.text = category.emoji
            b.categoryGradient.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(category.colors.first, category.colors.second)
            )
            val cover = covers[category.key]
            b.categoryImage.isVisible = cover != null
            if (cover != null) b.categoryImage.loadImage(cover)
            b.categoryCard.setOnClickListener { onClick(category) }
        }
    }
}
